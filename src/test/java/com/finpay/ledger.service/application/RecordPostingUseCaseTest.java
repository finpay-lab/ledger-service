package com.finpay.ledger.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.finpay.ledger.service.domain.Account;
import com.finpay.ledger.service.domain.AccountNotFoundException;
import com.finpay.ledger.service.domain.AccountType;
import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.EntrySpec;
import com.finpay.ledger.service.domain.IllegalPostingException;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingFactory;
import com.finpay.ledger.service.infrastructure.InMemoryAccountRepository;
import com.finpay.ledger.service.infrastructure.InMemoryPostingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordPostingUseCaseTest {

    private static final Instant POSTED_AT = Instant.parse("2026-08-12T06:34:22Z");

    private final InMemoryPostingRepository postings = new InMemoryPostingRepository();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final RecordPostingUseCase useCase =
            new RecordPostingUseCase(postings, accounts, new PostingFactory());

    private UUID debitAccount;
    private UUID creditAccount;

    @BeforeEach
    void setUp() {
        debitAccount = openAccount("EUR");
        creditAccount = openAccount("EUR");
    }

    private UUID openAccount(String currency) {
        UUID id = UUID.randomUUID();
        accounts.save(new Account(id, "test " + currency, currency, AccountType.ASSET,
                EntrySide.DEBIT, POSTED_AT));
        return id;
    }

    private List<EntrySpec> balancedLegs() {
        return List.of(
                new EntrySpec(debitAccount, EntrySide.DEBIT, new BigDecimal("150.00"), "EUR"),
                new EntrySpec(creditAccount, EntrySide.CREDIT, new BigDecimal("150.00"), "EUR"));
    }

    @Test
    void records_a_balanced_posting() {
        Posting posting = useCase.record("transfer-42", balancedLegs(), POSTED_AT);

        assertThat(posting.businessRef()).isEqualTo("transfer-42");
        assertThat(posting.entries()).hasSize(2);
        assertThat(posting.totalDebits()).isEqualByComparingTo("150.00");
        assertThat(posting.totalCredits()).isEqualByComparingTo("150.00");

        assertThat(postings.findByBusinessRef("transfer-42")).contains(posting);
    }

    @Test
    void replay_with_same_business_ref_returns_original_posting() {
        Posting first = useCase.record("transfer-42", balancedLegs(), POSTED_AT);

        Posting replay = useCase.record("transfer-42", balancedLegs(), POSTED_AT);

        assertThat(replay).isSameAs(first);
        assertThat(postings.findByBusinessRef("transfer-42")).hasValueSatisfying(p ->
                assertThat(p.entries()).hasSize(2));
    }

    @Test
    void unknown_account_is_rejected() {
        EntrySpec unknown = new EntrySpec(UUID.randomUUID(), EntrySide.DEBIT,
                new BigDecimal("10.00"), "EUR");
        List<EntrySpec> legs = List.of(unknown, balancedLegs().get(1));

        assertThatThrownBy(() -> useCase.record("unknown", legs, POSTED_AT))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void frozen_account_is_rejected() {
        Account frozen = accounts.findById(debitAccount).orElseThrow();
        frozen.freeze();

        assertThatThrownBy(() -> useCase.record("frozen", balancedLegs(), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("not OPEN");
    }

    @Test
    void closed_account_is_rejected() {
        Account closed = accounts.findById(debitAccount).orElseThrow();
        closed.close();

        assertThatThrownBy(() -> useCase.record("closed", balancedLegs(), POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("not OPEN");
    }

    @Test
    void currency_mismatch_with_account_is_rejected() {
        List<EntrySpec> legs = List.of(
                new EntrySpec(debitAccount, EntrySide.DEBIT, new BigDecimal("150.00"), "USD"),
                new EntrySpec(creditAccount, EntrySide.CREDIT, new BigDecimal("150.00"), "USD"));

        assertThatThrownBy(() -> useCase.record("currency", legs, POSTED_AT))
                .isInstanceOf(IllegalPostingException.class)
                .hasMessageContaining("currency");
    }
}