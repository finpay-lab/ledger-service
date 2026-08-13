package com.finpay.ledger.service.interfaces.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finpay.ledger.service.application.GetPostingUseCase;
import com.finpay.ledger.service.application.PostPostingResult;
import com.finpay.ledger.service.application.PostPostingUseCase;
import com.finpay.ledger.service.application.ReversePostingResult;
import com.finpay.ledger.service.application.ReversePostingUseCase;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingNotFoundException;
import com.finpay.ledger.service.domain.PostingStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controller slice tests: transport mapping + common-web problem details.
 * Use cases are mocked, so no DB/Kafka involved.
 */
@WebMvcTest(PostingController.class)
@Import(ApiExceptionHandler.class)
class PostingControllerWebTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PostPostingUseCase postPostingUseCase;

    @MockBean
    GetPostingUseCase getPostingUseCase;

    @MockBean
    ReversePostingUseCase reversePostingUseCase;

    private static final UUID POSTING_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();

    private static Posting posting() {
        return Posting.hydrate(
                POSTING_ID, "EUR", "key",
                Instant.now(), Instant.now(),
                List.of(
                        new LedgerEntry(UUID.randomUUID(), POSTING_ID, ACCOUNT_A, EntrySide.DEBIT,
                                new BigDecimal("100.00"), "EUR"),
                        new LedgerEntry(UUID.randomUUID(), POSTING_ID, ACCOUNT_B, EntrySide.CREDIT,
                                new BigDecimal("100.00"), "EUR")),
                PostingStatus.POSTED, null, null, 0L);
    }

    private static Posting reversedOriginal() {
        return Posting.hydrate(
                POSTING_ID, "EUR", "key",
                Instant.now(), Instant.now(),
                List.of(
                        new LedgerEntry(UUID.randomUUID(), POSTING_ID, ACCOUNT_A, EntrySide.DEBIT,
                                new BigDecimal("100.00"), "EUR"),
                        new LedgerEntry(UUID.randomUUID(), POSTING_ID, ACCOUNT_B, EntrySide.CREDIT,
                                new BigDecimal("100.00"), "EUR")),
                PostingStatus.REVERSED, null, "booked twice", 0L);
    }

    @Test
    void post_requires_idempotency_key_header() throws Exception {
        mvc.perform(post("/api/v1/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\",\"legs\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void post_rejects_imbalanced_legs_at_boundary() throws Exception {
        when(postPostingUseCase.post(any())).thenThrow(new IllegalArgumentException(
                "double-entry posting must balance: debit 100.00 != credit 99.00"));

        mvc.perform(post("/api/v1/postings")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\",\"legs\":["
                                + "{\"accountId\":\"" + ACCOUNT_A + "\",\"side\":\"DEBIT\",\"amount\":100.00},"
                                + "{\"accountId\":\"" + ACCOUNT_B + "\",\"side\":\"CREDIT\",\"amount\":99.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void post_returns_201_with_posting_body() throws Exception {
        when(postPostingUseCase.post(any())).thenReturn(new PostPostingResult(posting(), true));

        mvc.perform(post("/api/v1/postings")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\",\"legs\":["
                                + "{\"accountId\":\"" + ACCOUNT_A + "\",\"side\":\"DEBIT\",\"amount\":100.00},"
                                + "{\"accountId\":\"" + ACCOUNT_B + "\",\"side\":\"CREDIT\",\"amount\":100.00}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postingId").value(POSTING_ID.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void get_posting_404_maps_to_problem_details() throws Exception {
        when(getPostingUseCase.get(POSTING_ID)).thenThrow(new PostingNotFoundException(POSTING_ID));

        mvc.perform(get("/api/v1/postings/" + POSTING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    void reverse_rejects_already_reversed_posting_with_409() throws Exception {
        when(reversePostingUseCase.reverse(any()))
                .thenThrow(new com.finpay.ledger.service.domain.IllegalStateTransitionException(
                        PostingStatus.REVERSED, PostingStatus.REVERSED));

        mvc.perform(post("/api/v1/postings/" + POSTING_ID + "/reversals")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"booked twice\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void legal_reversal_returns_original_and_reversal_ids() throws Exception {
        Posting reversal = Posting.hydrate(
                UUID.randomUUID(), "EUR", "rev-key",
                Instant.now(), Instant.now(),
                List.of(
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_A, EntrySide.CREDIT,
                                new BigDecimal("100.00"), "EUR"),
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_B, EntrySide.DEBIT,
                                new BigDecimal("100.00"), "EUR")),
                PostingStatus.POSTED, POSTING_ID, "booked twice", 0L);
        when(reversePostingUseCase.reverse(any()))
                .thenReturn(new ReversePostingResult(reversal, reversedOriginal(), true));

        mvc.perform(post("/api/v1/postings/" + POSTING_ID + "/reversals")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"booked twice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalPostingId").value(POSTING_ID.toString()))
                .andExpect(jsonPath("$.reversalPostingId").value(reversal.postingId().toString()))
                .andExpect(jsonPath("$.originalStatus").value("REVERSED"));
    }
}