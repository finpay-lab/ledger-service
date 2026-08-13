package com.finpay.ledger.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.finpay.ledger.service.domain.DomainEvent;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingNotFoundException;
import com.finpay.ledger.service.domain.PostingRepository;
import com.finpay.ledger.service.domain.PostingStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ReversePostingUseCaseTest {

    @Mock
    private PostingRepository postingRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private ReversePostingUseCase useCase;

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();

    private Posting original;

    @BeforeEach
    void setUp() {
        original = Posting.hydrate(
                UUID.randomUUID(), "EUR", "orig-key", Instant.now(), Instant.now(),
                List.of(
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_A,
                                EntrySide.DEBIT, new BigDecimal("200.00"), "EUR"),
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_B,
                                EntrySide.CREDIT, new BigDecimal("200.00"), "EUR")),
                PostingStatus.POSTED, null, null, 0L);
    }

    @Test
    void reverse_creates_offsetting_posting_marks_original_and_publishes_events() {
        when(postingRepository.findById(original.postingId())).thenReturn(Optional.of(original));

        ReversePostingResult result = useCase.reverse(
                new ReversePostingCommand(original.postingId(), "rev-key", "booked twice"));

        assertThat(result.created()).isTrue();
        assertThat(result.original().status()).isEqualTo(PostingStatus.REVERSED);
        assertThat(result.reversal().reversalOfPostingId()).isEqualTo(original.postingId());
        // Original updated + reversal inserted, atomically.
        verify(postingRepository, times(2)).save(any(Posting.class));

        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(captor.capture());
        List<DomainEvent> events = captor.getValue();
        assertThat(events).hasSize(3); // 2 reversal LedgerEntryPosted + 1 LedgerReversed
        assertThat(events).anyMatch(e -> e.eventType().equals("LedgerReversed"));
    }

    @Test
    void repeated_idempotency_key_returns_existing_reversal_without_side_effects() {
        Posting reversal = Posting.createReversal(original, "rev-key", "booked twice");
        original.markReversed(reversal.postingId(), "booked twice");
        when(postingRepository.findByIdempotencyKey("rev-key")).thenReturn(Optional.of(reversal));
        when(postingRepository.findById(reversal.reversalOfPostingId())).thenReturn(Optional.of(original));

        ReversePostingResult result = useCase.reverse(
                new ReversePostingCommand(original.postingId(), "rev-key", "booked twice"));

        assertThat(result.created()).isFalse();
        assertThat(result.reversal()).isSameAs(reversal);
        verify(postingRepository, never()).save(any(Posting.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void missing_posting_is_rejected() {
        UUID unknown = UUID.randomUUID();
        when(postingRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reverse(new ReversePostingCommand(unknown, "k", "why")))
                .isInstanceOf(PostingNotFoundException.class);
    }

    @Test
    void already_reversed_posting_is_rejected() {
        Posting reversal = Posting.createReversal(original, "rev-key", "first");
        original.markReversed(reversal.postingId(), "first");
        when(postingRepository.findById(original.postingId())).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> useCase.reverse(
                new ReversePostingCommand(original.postingId(), "rev-key-2", "second")))
                .isInstanceOf(com.finpay.ledger.service.domain.IllegalStateTransitionException.class);
    }

    @Test
    void missing_posting_id_is_rejected() {
        assertThatThrownBy(() -> useCase.reverse(new ReversePostingCommand(null, "k", "why")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}