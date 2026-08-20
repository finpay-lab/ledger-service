package com.finpay.ledger.service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingUseCaseTest {

    Currency USD = Currency.getInstance("USD");

    /** In-memory fake of the domain ports (no DB). */
    static final class FakeRepo implements LedgerRepository {
        final List<Entry> entries = new ArrayList<>();
        final List<String> posted = new ArrayList<>();
        java.util.Map<String, Account> accts = new java.util.HashMap<>();
        @Override public Optional<Account> findAccount(String id) { return Optional.ofNullable(accts.get(id)); }
        @Override public Account saveAccount(Account a) { accts.put(a.accountId(), a); return a; }
        @Override public void append(Entry e) { entries.add(e); }
        @Override public List<Entry> entriesFor(String id) { return entries.stream().filter(x->x.accountId().equals(id)).toList(); }
        @Override public BigDecimal balanceOf(String id) { return entries.stream().filter(x->x.accountId().equals(id)).map(Entry::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
        @Override public boolean postingExists(String k) { return posted.contains(k); }
        @Override public void markPosted(String k, String t) { posted.add(k); }
    }

    static final class FakeOutbox implements Outbox {
        final List<String> staged = new ArrayList<>();
        @Override public void stage(String t, String a, String p) { staged.add(t + ":" + a); }
    }

    private Account acct(String id) {
        return new Account(id, "owner-" + id, USD, Account.AccountStatus.OPEN);
    }

    @Test
    void postingCreatesDoubleEntryAndBalance() {
        FakeRepo repo = new FakeRepo();
        repo.saveAccount(acct("A")); repo.saveAccount(acct("B"));
        FakeOutbox outbox = new FakeOutbox();
        PostingUseCase uc = new PostingUseCase(repo, outbox);

        var res = uc.post(new PostingUseCase.PostingRequest(
                "key-1", "txn-1", "A", "B", new BigDecimal("100.00"), "USD", " payment"));

        assertThat(res.transactionId()).isEqualTo("txn-1");
        // one debit + one credit
        assertThat(repo.entries).hasSize(2);
        assertThat(repo.entries.stream().filter(e -> e.type() == Entry.EntryType.DEBIT).count()).isEqualTo(1);
        assertThat(repo.entries.stream().filter(e -> e.type() == Entry.EntryType.CREDIT).count()).isEqualTo(1);
        assertThat(repo.balanceOf("A")).isEqualByComparingTo("-100.00");
        assertThat(repo.balanceOf("B")).isEqualByComparingTo("100.00");
        // outbox staged LedgerEntryPosted
        assertThat(outbox.staged).anyMatch(s -> s.startsWith("LedgerEntryPosted:"));
    }

    @Test
    void idempotencyRejectsDuplicateKey() {
        FakeRepo repo = new FakeRepo();
        repo.saveAccount(acct("A")); repo.saveAccount(acct("B"));
        PostingUseCase uc = new PostingUseCase(repo, new FakeOutbox());

        uc.post(new PostingUseCase.PostingRequest("same-key", "t1", "A", "B", new BigDecimal("10"), "USD", "x"));
        var second = uc.post(new PostingUseCase.PostingRequest("same-key", "t2", "A", "B", new BigDecimal("20"), "USD", "y"));

        // duplicate -> no new entries
        assertThat(repo.entries).hasSize(2);
        assertThat(second.debitEntryId()).isEqualTo("duplicate");
    }

    @Test
    void postingUnknownAccountFails() {
        PostingUseCase uc = new PostingUseCase(new FakeRepo(), new FakeOutbox());
        assertThatThrownBy(() -> uc.post(new PostingUseCase.PostingRequest(
                "k", "t", "MISSING", "B", new BigDecimal("1"), "USD", "x")))
                .isInstanceOf(PostingUseCase.AccountNotFound.class);
    }
}
