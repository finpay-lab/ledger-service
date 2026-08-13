package com.finpay.ledger.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.finpay.ledger.service.domain.DomainEvent;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingLeg;
import com.finpay.ledger.service.domain.PostingRepository;

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
class PostPostingUseCaseTest {

    @Mock
    private PostingRepository postingRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private PostPostingUseCase useCase;

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();

    @Test
    void posts_and_publishes_outbox_events() {
        var command = new PostPostingCommand(
                List.of(
                        new PostingLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("100.00")),
                        new PostingLeg(ACCOUNT_B, EntrySide.CREDIT, new BigDecimal("100.00"))),
                "EUR", "key-1");

        PostPostingResult result = useCase.post(command);

        assertThat(result.created()).isTrue();
        assertThat(result.posting().entries()).hasSize(2);
        verify(postingRepository).save(any(Posting.class));

        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void repeated_idempotency_key_returns_existing_without_side_effects() {
        Posting existing = Posting.hydrate(
                UUID.randomUUID(), "EUR", "key-2", Instant.now(), Instant.now(),
                List.of(
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_A,
                                EntrySide.DEBIT, new BigDecimal("50.00"), "EUR"),
                        new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT_B,
                                EntrySide.CREDIT, new BigDecimal("50.00"), "EUR")),
                com.finpay.ledger.service.domain.PostingStatus.POSTED, null, null, 0L);
        when(postingRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.of(existing));

        var command = new PostPostingCommand(
                List.of(
                        new PostingLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("50.00")),
                        new PostingLeg(ACCOUNT_B, EntrySide.CREDIT, new BigDecimal("50.00"))),
                "EUR", "key-2");

        PostPostingResult result = useCase.post(command);

        assertThat(result.created()).isFalse();
        assertThat(result.posting()).isSameAs(existing);
        verify(postingRepository, never()).save(any(Posting.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void invalid_payload_is_rejected() {
        var imbalanced = new PostPostingCommand(
                List.of(
                        new PostingLeg(ACCOUNT_A, EntrySide.DEBIT, new BigDecimal("10.00")),
                        new PostingLeg(ACCOUNT_B, EntrySide.CREDIT, new BigDecimal("9.00"))),
                "EUR", "k");
        assertThatThrownBy(() -> useCase.post(imbalanced)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.post(null)).isInstanceOf(IllegalArgumentException.class);
    }
}