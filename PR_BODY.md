## Summary
Implements the FP-4 ledger core: the `Account` and immutable `Entry` domain
model plus a balanced double-entry `Posting` aggregate for ledger-service. A
posting can never leave the ledger unbalanced (sum of debits == sum of credits),
entries are write-once, and account/status restrictions are enforced before a
posting is recorded. This is the first slice of project task TASK-020.

## Changes
- `src/main/java/com/finpay/ledger.service/domain/`
  - `Account` — ledger account entity with ISO-4217 currency, accounting type,
    normal balance side, and a status state machine (`OPEN`/`FROZEN`/`CLOSED`)
    that rejects illegal transitions (Rule 9).
  - `AccountStatus`, `AccountType`, `EntrySide` — enums backing the model.
  - `Entry` — immutable double-entry leg (package-private constructor, value
    semantics, positive-amount + currency validation).
  - `Posting` — immutable, balanced posting aggregate; exposes an unmodifiable
    leg list and debit/credit totals.
  - `PostingFactory` — domain service that enforces the double-entry invariant
    (>= 2 legs, same currency, positive amounts, debits == credits) and rejects
    violations with `IllegalPostingException`.
  - `EntrySpec` — request leg record consumed by the factory/use case.
  - `AccountRepository`, `PostingRepository` — domain repository interfaces.
  - `LedgerDomainException`, `IllegalPostingException`, `AccountNotFoundException`.
- `src/main/java/com.finpay/ledger.service/application/RecordPostingUseCase.java`
  — records postings idempotently by `businessRef` (Rule 6); validates the
  account exists, is `OPEN`, and matches the posting currency.
- `src/main/java/com.finpay/ledger.service/infrastructure/`
  - `InMemoryAccountRepository`, `InMemoryPostingRepository` — thread-safe
    stand-ins until JPA/Flyway persistence lands (TASK-021); save is
    put-if-absent to guarantee idempotent replay.
- `build.gradle` — added `testImplementation libs.common.test` (shared ArchUnit
  architecture rules).
- Tests: `AccountTest`, `EntryTest`, `PostingFactoryTest`, `RecordPostingUseCaseTest`,
  `LedgerArchitectureTest` (ArchUnit: domain free of Spring/JPA/Kafka).

## Testing
- `docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/root/.gradle \
  gradle:8.10.2-jdk21 gradle clean build -Pversion=0.0.1 --no-daemon`
  → BUILD SUCCESSFUL.
- 28 tests green (6+6+8 domain, 6 use-case incl. idempotent replay, frozen/
  closed/unknown-account rejection, 1 ArchUnit, 1 pre-existing smoke test).

## Risks
- Posting creation is not yet persisted to PostgreSQL (in-memory repositories);
  the real persistence + balance row with optimistic locking is TASK-021.
- Idempotent replay returns the original posting rather than detecting a
  payload mismatch (`IDEMPOTENCY_CONFLICT`); a mismatch policy can be layered on
  when the DB-backed repository lands.
- No REST/outbox surface yet — that arrives with TASK-022.