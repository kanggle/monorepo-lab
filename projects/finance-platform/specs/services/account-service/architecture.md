# account-service — Architecture

This document declares the internal architecture of `finance-platform/apps/account-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the rule files indexed by
`PROJECT.md`'s declared `domain` (`fintech`) and `traits`
(`transactional`, `regulated`, `audit-heavy`).

> **Provenance**: Authored by [TASK-FIN-BE-001](../../../tasks/review/) **before**
> implementation (HARDSTOP-09 — architecture decision precedes code). The
> account-service skeleton (`@SpringBootApplication`, `application.yml`,
> empty `db/migration/`) and the IAM `finance-platform-internal-services-client`
> + `finance` tenant V0017 seed shipped in [TASK-MONO-114](../../../../../tasks/done/)
> (ADR-MONO-008 ACCEPTED, Option C). Sections describe the **target v1
> implementation**; the impl PR follows this spec.

---

## Identity

| Field | Value |
|---|---|
| Service Name | `account-service` |
| Project | `finance-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Domain | fintech |
| Traits | transactional, regulated, audit-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Account + Wallet/Balance + Transaction + KYC/Compliance (v1 single deployable; Ledger/Reconciliation forward-declared) |
| Deployable unit | `apps/account-service/` |
| Data store | MySQL `finance_db` (Flyway) + Redis (idempotency / cache) |
| Event publication | Kafka via transactional outbox |
| Outbound integration | None real in v1 — KYC/AML/bank ports defined, mock/stub adapters only (real providers = v2) |

### Service Type Composition

`account-service` is a single-type **`rest-api`** service per
[`platform/service-types/INDEX.md`](../../../../../platform/service-types/INDEX.md).
All v1 responsibilities (account lifecycle, balance hold/release/capture,
fund movement, KYC gate) are exposed through the synchronous HTTP
request/response surface. Kafka publication is a **side effect** of REST
mutations through the transactional outbox and, per
`platform/service-types/INDEX.md` ("REST service that also publishes events
→ `rest-api`"), **does not promote the service to `event-consumer`** —
identical reasoning to `scm-platform/procurement-service`.

**Reconciliation of `TASK-FIN-BE-001 §1` framing** — the task's initial
"Service Type = rest-api + event-consumer" wording is refined here (the
architecture.md is the authoritative place for the Service Type decision,
HARDSTOP-09/10; CLAUDE.md Source-of-Truth: `platform/service-types/` > task).
v1 account-service does not *primarily react to* inbound domain events; it
publishes its own. `PROJECT.md`'s project-level `service_types:
[rest-api, event-consumer]` reserves `event-consumer` for the **v2
`notification-service`** (event fan-out consumer), not account-service v1.
If a v1 inbound subscription is later added it becomes
`rest-api + event-consumer (<trigger>)` — a clarification, not a
re-classification (INDEX.md note 2).

---

## Responsibilities

`account-service` owns the v1 **Account lifecycle, balance, and fund-movement**
core for finance-platform. It MUST:

- Open accounts (`PENDING_KYC`), track KYC level, and drive the account state
  machine (§ Account State Machine) — every transition append-only audited (F6).
- Maintain **available vs ledger balance** with `hold` / `release` / `capture`
  as the single atomic balance-mutation entry point (F2).
- Process fund movements (`hold`, `capture`, `release`, `transfer`) as
  idempotent transactions driven by a client `Idempotency-Key`, with the
  balance change + transaction state transition + event publish in one
  transaction via the outbox (F1).
- Enforce the **KYC/AML compliance gate before every fund movement** —
  KYC-level + AML/sanction screening port; failure → `KYC_*` / `AML_*` /
  `SANCTION_HIT`, sanction hit routed to an operator queue (F4).
- Represent all money as integer **minor-units** (`long`) carried with an
  ISO-4217 currency; never `float`/`double` (F5).
- Append every fund/regulatory-affecting operation to an immutable
  append-only `audit_log` (actor / timestamp / before / after / reason) (F6).
- Encrypt regulated PII / financial identifiers at rest; mask in
  logs/events/errors (F7).
- Validate IAM RS256 JWT (OAuth2 Resource Server) and fail-closed on
  `tenant_id != finance` (defense-in-depth, § Multi-tenancy).
- Publish `finance.account.* / finance.balance.* / finance.transaction.*`
  Kafka events through the transactional outbox.

It MUST NOT:

- Implement double-entry ledger / GL / AP — `ledger-service` v2
  (ADR-MONO-008 § D3; F2/F3 forward-decl only).
- Own wallet partitioning, KYC provider integration, notification fan-out, or
  the operator admin console — `wallet-service` / `kyc-service` /
  `notification-service` / `admin-service` v2.
- Auto-close reconciliation discrepancies or adjust differences (F8) — the
  reconciliation model is forward-declared; real external settlement is v2.
- Couple to external bank / KYC / sanction vendor SDKs in `domain/` or
  `application/` (must stay behind `infrastructure/` ports).
- Optimistically confirm an ambiguous external fund-movement response
  (fintech Forbidden Pattern) — v1 has no real external adapter; the port
  contract documents the "indeterminate, reconcile later" rule for v2.

---

## Architecture Style Rationale

**Hexagonal (Ports & Adapters)** chosen because:

1. **Compliance + persistence + messaging are swappable outbound concerns** —
   KYC/AML screening (`CompliancePort`), idempotency store
   (`IdempotencyStore`), event publication (outbox), and (v2) external
   bank/settlement adapters all sit behind ports. v1 ships mock/stub
   compliance + the real MySQL/Redis adapters; v2 swaps real providers
   without touching the domain.
2. **Fund-movement invariants must be framework-free and exhaustively
   unit-tested** — `Account`, `Money`, `Balance`, `Hold`, `Transaction`, and
   the two state machines are pure Java (no Spring/JPA in transition logic)
   so the F1–F5 invariants are provable by fast unit tests.
3. **The compliance gate must be un-bypassable** — a single `application/`
   command path funnels every fund movement through the `CompliancePort`;
   Hexagonal makes "no other path to balance mutation" structurally
   enforceable (F4).
4. **Testability** — domain unit (no Spring) + application unit (mock ports) +
   `@WebMvcTest` slice + Testcontainers integration (MySQL + Redis,
   WireMock JWKS). H2 is forbidden (parity with production MySQL).

Aligns with `platform/architecture-decision-rule.md` and the default
Hexagonal expectation for `transactional` services. `gateway-service`
(v1 deferred) will be the single intentional Layered exception.

---

## Layer Structure

Hexagonal variant — `presentation/` is the inbound web adapter,
`infrastructure/` aggregates outbound adapters + config. Root package
`com.example.finance.account` (matches the TASK-MONO-114 skeleton).

```
com.example.finance.account/
├── AccountServiceApplication.java          ← skeleton (TASK-MONO-114)
├── domain/                                 ← pure Java, no framework
│   ├── account/
│   │   ├── Account.java                    ← aggregate root
│   │   ├── AccountId.java
│   │   ├── KycLevel.java                   ← NONE / BASIC / FULL (limit tiers)
│   │   ├── repository/AccountRepository.java       ← outbound port
│   │   └── status/
│   │       ├── AccountStatus.java          ← PENDING_KYC/ACTIVE/RESTRICTED/FROZEN/CLOSED
│   │       ├── AccountStatusMachine.java   ← transition matrix (pure)
│   │       └── AccountStatusHistory.java
│   ├── balance/
│   │   ├── Balance.java                    ← ledger + available + held (VO)
│   │   ├── Hold.java                       ← hold lifecycle (ACTIVE/CAPTURED/RELEASED/EXPIRED)
│   │   └── repository/BalanceRepository.java
│   ├── transaction/
│   │   ├── Transaction.java                ← aggregate root
│   │   ├── TransactionType.java            ← TOPUP/WITHDRAW/TRANSFER/HOLD/CAPTURE/RELEASE/REVERSAL
│   │   ├── repository/TransactionRepository.java
│   │   └── status/
│   │       ├── TransactionStatus.java      ← REQUESTED…COMPLETED + FAILED/REVERSED
│   │       └── TransactionStatusMachine.java
│   ├── money/
│   │   ├── Money.java                      ← long minorUnits + Currency (NO float/double)
│   │   └── Currency.java                   ← ISO-4217 + minor-unit scale (KRW=0,USD=2)
│   ├── compliance/
│   │   ├── KycGate.java                    ← KYC-level vs requested-op policy (pure)
│   │   └── ScreeningDecision.java          ← AML/sanction result VO
│   ├── audit/
│   │   ├── AuditLog.java
│   │   └── AuditLogRepository.java
│   └── error/                              ← domain exceptions (fintech codes)
│       (AccountNotFoundException, AccountNotActiveException,
│        InsufficientAvailableBalanceException, HoldAlreadySettledException,
│        TransactionStatusTransitionInvalidException, IdempotencyKeyConflictException,
│        CurrencyMismatchException, KycRequiredException, KycLevelInsufficientException,
│        SanctionHitException, ...)
├── application/                            ← use cases + outbound ports
│   ├── AccountApplicationService.java      ← @Transactional command boundary
│   ├── ActorContext.java
│   ├── view/ (AccountView, BalanceView, TransactionView)  ← read-model DTOs
│   ├── command/                            ← OpenAccount/Hold/Capture/Release/Transfer/UpgradeKyc
│   ├── event/
│   │   └── AccountEventPublisher.java      ← outbox-append port (impl in infrastructure/outbox)
│   └── port/outbound/
│       ├── CompliancePort.java             ← KYC/AML/sanction screening (v1 stub adapter)
│       ├── IdempotencyStore.java           ← Redis-or-DB dedupe port
│       └── ClockPort.java
├── infrastructure/                         ← outbound adapters + config
│   ├── persistence/jpa/                    ← Spring Data + adapter beans (toDomain/fromDomain)
│   │   (AccountJpaEntity/Repository/Adapter, BalanceJpaEntity..., TransactionJpaEntity...,
│   │    AuditLogJpaEntity..., idempotency_keys; retained-unused outbox + processed_events stubs)
│   ├── outbox/AccountOutboxJpaEntity + AccountOutboxJpaRepository  ← v2 account_outbox (OutboxRow)
│   ├── outbox/OutboxAccountEventPublisher.java     ← AccountEventPublisher impl (envelope + append)
│   ├── outbox/AccountOutboxPublisher.java          ← extends libs AbstractOutboxPublisher (relay)
│   ├── compliance/StubComplianceAdapter.java       ← v1 in-process screening (deterministic)
│   ├── crypto/PiiEncryptor.java            ← AES-256-GCM column encryption (F7)
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── ServiceLevelOAuth2Config.java
│   │   ├── AllowedIssuersValidator.java
│   │   ├── TenantClaimValidator.java
│   │   ├── ActorContextResolver.java
│   │   └── ActorContextJwtAuthenticationConverter.java
│   └── config/ (ClockConfig, JpaConfig)
└── presentation/                           ← inbound web adapter
    ├── controller/
    │   ├── AccountController.java          ← /api/finance/accounts/**
    │   └── TransactionController.java      ← /api/finance/accounts/{id}/{hold,capture,release,transfer}
    ├── advice/GlobalExceptionHandler.java  ← domain → HTTP envelope (fintech codes)
    ├── dto/                                ← request / response DTOs (money as String/integer)
    ├── filter/TenantClaimEnforcer.java     ← service-level fail-closed
    └── security/PublicPaths.java
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `org.springframework.kafka:spring-kafka`
- `org.flywaydb:flyway-core`, `flyway-mysql`, `com.mysql:mysql-connector-j` (runtime)
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`,
  `io.opentelemetry:opentelemetry-exporter-otlp`
- `com.fasterxml.jackson.{core:jackson-databind, datatype:jackson-datatype-jsr310}`
- `net.logstash.logback:logstash-logback-encoder` (prod profile)
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`,
  `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- Bank / KYC / sanction vendor SDKs in `domain/` or `application/` — must be
  behind `infrastructure/` ports (fintech Integration Boundaries).
- `float` / `double` anywhere in money representation, calculation, JSON
  (F5 — `Money` VO only).
- Persistence frameworks beyond `spring-boot-starter-data-{jpa,redis}` — no
  reactive variants (Servlet stack).
- Direct `ledger`/double-entry tables — `ledger-service` v2 owns accounting
  depth; v1 must not pre-build a half ledger.

### Boundary rules

- `domain/` MUST NOT depend on Spring (JPA annotations on entities are the
  single allowed exception; state machines + `Money` are pure).
- `application/AccountApplicationService` is the **only** `@Transactional`
  command boundary — controllers MUST NOT carry `@Transactional`.
- Every fund movement MUST pass through the single application path that
  invokes `CompliancePort` then mutates `Balance` — no other balance-mutation
  entry point exists (F2/F4 structural enforcement).
- `presentation/controller/` MUST NOT touch JPA repositories directly — all
  persistence flows through `application/` use cases.
- `infrastructure/compliance/StubComplianceAdapter` is the only v1 screening
  path; the application consumes it through `CompliancePort` exclusively (v2
  real provider swaps the adapter, not the port).
- `presentation/filter/TenantClaimEnforcer` is defense-in-depth only —
  gateway + JWT validator chain are the primary tenant gate.

---

## Account State Machine

`AccountStatus` (5 states); `AccountStatusMachine.ensureTransitionAllowed`
enforces the matrix. Self-transitions forbidden. `CLOSED` is terminal.

```
PENDING_KYC
  ├─(KYC reaches required level)→ ACTIVE
  └─(operator/compliance)→ CLOSED ★
ACTIVE
  ├─(risk/compliance restrict)→ RESTRICTED
  ├─(compliance/operator freeze)→ FROZEN
  └─(holder/operator close, zero balance)→ CLOSED ★
RESTRICTED
  ├─(restriction cleared)→ ACTIVE
  ├─(escalate)→ FROZEN
  └─(operator close)→ CLOSED ★
FROZEN
  ├─(investigation cleared)→ ACTIVE
  └─(operator close)→ CLOSED ★
```

★ terminal. Only `ACTIVE` accounts permit fund movement;
`PENDING_KYC`→`KYC_REQUIRED`, `RESTRICTED`→outbound-only policy,
`FROZEN`→`ACCOUNT_FROZEN`, `CLOSED`→`ACCOUNT_NOT_ACTIVE`. `CLOSE` requires
zero ledger + available + zero active holds. Every transition writes
`account_status_history` + `audit_log` in the same Tx (F6).

## Transaction State Machine

`TransactionStatus`; `TransactionStatusMachine` enforces:

```
REQUESTED ─(validate: account ACTIVE, currency, amount)→ VALIDATED
VALIDATED ─(KYC/AML gate pass)→ AUTHORIZED            │ ─(gate fail)→ FAILED ★
AUTHORIZED ─(balance applied: hold/capture/release/transfer)→ SETTLED
SETTLED ─(post-effects, event emitted)→ COMPLETED ★
(any pre-SETTLED) ─(error)→ FAILED ★
COMPLETED ─(operator reversal: NEW reversal txn)→ REVERSED ★ (original immutable)
```

★ terminal. `SETTLED`/`COMPLETED` are **immutable** — correction is a new
`REVERSAL` transaction referencing the original; both audited (F3). The
balance mutation + transaction transition + outbox event are one Tx (F1).

## Balance Model (F2)

`Balance` per (accountId, currency): `ledgerMinor` (confirmed),
`heldMinor` (sum of ACTIVE holds), `available = ledger − held`.

| Op | Effect |
|---|---|
| `hold(amount)` | `available ≥ amount` else `INSUFFICIENT_AVAILABLE_BALANCE`; `held += amount`; Hold→ACTIVE |
| `capture(holdId, ≤holdAmount)` | `ledger −= captured`; `held −= holdAmount`; Hold→CAPTURED (partial = remainder released) |
| `release(holdId)` | `held −= holdAmount`; Hold→RELEASED (funds back to available) |
| `transfer(from→to, amount)` | atomic hold-on-source + capture + credit-target in one Tx; cross-currency → `CURRENCY_MISMATCH` |
| `topup` / `withdraw` | `ledger ±= amount` (withdraw guarded by available; v1 = internal/stub funding source) |

`available` never negative. Balance mutated at exactly one place
(`AccountApplicationService` → domain `Balance`); no other writer. Expired
holds auto-`release` (sweep policy: `HoldExpirySweeper`, deferred-impl flag
documented in impl PR). Double-entry/ledger = `ledger-service` v2
(`LEDGER_ENTRY_UNBALANCED` forward-declared in fintech.md, not v1).

## KYC / AML Compliance Gate (F4)

Single un-bypassable gate inside the fund-movement application path,
**before** any balance mutation:

1. `KycGate.permits(account.kycLevel, txType, amount)` — per-level limits
   (NONE = none; BASIC = capped; FULL = standard). Fail →
   `KYC_REQUIRED` / `KYC_LEVEL_INSUFFICIENT` / `TRANSACTION_LIMIT_EXCEEDED`.
2. `CompliancePort.screen(account, counterparty, amount)` — AML/sanction.
   `AML_SCREENING_REQUIRED` if unresolved; `SANCTION_HIT` → transaction
   `FAILED` + operator-queue row (`compliance_review_queue`, no auto-clear)
   + `finance.compliance.sanction.hit` event. v1 `StubComplianceAdapter` is
   deterministic (configurable sanction list for tests); v2 = real provider
   behind the same port.

No code path mutates balance without traversing this gate (Boundary rule).

## Idempotency (F1)

All mutating endpoints require `Idempotency-Key` (missing → 400
`IDEMPOTENCY_KEY_REQUIRED`). `IdempotencyStore` port: Redis primary (SET
NX-EX), `idempotency_keys` table fallback when Redis is offline
(fail-CLOSED → 503 `IDEMPOTENCY_STORE_UNAVAILABLE` if both down). Same key +
identical payload → first stored response replayed (no fund re-movement);
same key + different payload → 409 `IDEMPOTENCY_KEY_CONFLICT`. Key scope =
`(idempotency_key, endpoint, tenant_id)`.

## Outbox + audit_log invariants

Transactional outbox v2 (libs/java-messaging `AbstractOutboxPublisher` — the
`OutboxRow` path, ADR-MONO-004 § 5; TASK-FIN-BE-045, mirroring ledger-service):
the `AccountEventPublisher` port impl (`OutboxAccountEventPublisher`) builds the
canonical envelope and appends an `account_outbox` row inside the use-case
`@Transactional` boundary (F1); the `AccountOutboxPublisher` relay forwards rows
to Kafka with exponential backoff + `eventId`/`eventType` headers and stamps
`published_at` after the ACK. The on-wire envelope is the preserved 7-field shape
(`{eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload}`)
with Source = `"finance-platform-account-service"`. Topics → § contract
`finance-account-events.md`. `audit_log` (append-only, no UPDATE/DELETE,
written in the same Tx) records account status transitions, balance
mutations, transaction settle/reversal, KYC-level changes, sanction-queue
entries — `actor / occurred_at / before_state / after_state / reason` (F6).
`account_status_history` is the account-specific transition log; both
append-only.

## Reconciliation (F8 — forward-declared)

v1 has no real external settlement source, so reconciliation is
**modelled, not executed**: a `reconciliation_discrepancy` table + the rule
that discrepancies enter `compliance_review_queue` (operator) and are never
auto-closed. Real external-statement matching = v2 (`ledger-service` /
`admin-service`). Documented so v2 does not re-derive the policy.

## Multi-tenancy

finance-platform is **not** internally multi-tenant (single financial
service); IAM supplies `tenant_id=finance`. Defense-in-depth (mirrors scm):

1. **Gateway** (v1 deferred) — domain gate at JWT decode.
2. **Service JWT validator chain** — `AllowedIssuersValidator` (SAS issuer +
   legacy `iam-platform` D2-b window — byte-identical to the
   future gateway's allowed-issuers) + `TenantClaimValidator`.
3. **Service filter** — `TenantClaimEnforcer` → 403 `TENANT_FORBIDDEN` when
   the gate rejects (public paths skipped).

**Domain gate — entitlement-trust dual-accept** (ADR-MONO-019 § D5, pilot
domain). Both enforcement points (`TenantClaimValidator` at decode time and
`TenantClaimEnforcer` filter) apply the *same* rule via the shared
`TenantClaimValidator.isEntitled(jwt, domain)` helper (single source of truth
— a split would let entitled traffic pass decode yet be blocked by the
filter). A token is accepted when **either**:

- **(legacy slug)** `tenant_id ∈ {finance, *}` — `*` is SUPER_ADMIN
  platform-scope; **or**
- **(entitlement-trust)** the IAM-signed `entitled_domains` claim (a list of
  domain keys) contains `finance`.

Rejection (403 `TENANT_FORBIDDEN`) requires **both** branches to fail
(fail-closed; entitlement only *widens* the allowed set, never weakens the
legacy reject). `entitled_domains` is read only from an RS256/JWKS-verified
token, so it is unforgeable — **IAM is the entitlement authority**; a
non-list / null / empty / non-string-element claim degrades to "not entitled".
Row-level isolation is unchanged: `ActorContextJwtAuthenticationConverter`
still keys row scoping off `tenant_id`, so an entitled cross-slug token sees
only its own `tenant_id` partition. While IAM has not yet populated
`entitled_domains` the claim is absent → only the legacy path applies →
**production net-zero**. This is the ADR-MONO-019 **dual-accept window**; the
legacy `tenant_id == slug` branch is removed in step 4 once IAM populates the
claim (separate follow-up).

Config keys (TASK-MONO-114 skeleton `application.yml`):
`financeplatform.oauth2.allowed-issuers` + `.required-tenant-id=finance`.
Every table carries `tenant_id VARCHAR(64) NOT NULL`; repository methods
always embed `tenant_id` in `WHERE` (no tenant-omitting method exists).

## Security

- **JWT (RS256)**: `oauth2-resource-server` against
  `${OIDC_ISSUER_URL:http://iam.local}/oauth2/jwks`; RS256 only;
  `JwtTimestampValidator` + `AllowedIssuersValidator` + `TenantClaimValidator`.
  IAM `finance-platform-internal-services-client` (client_credentials,
  scopes `finance.read`/`finance.write`, V0017) is the v1 caller.
- **Column encryption (F7)**: `PiiEncryptor` AES-256-GCM
  (`[12-byte IV][ciphertext][16-byte tag]`) on regulated identifiers
  (external account refs, KYC document refs). Key from
  `financeplatform.account.crypto.pii-key` (env override); per-row
  `encryption_key_id` (`"v1"`); boot self-test fails fast on
  misconfiguration. Plaintext PII never logged/evented/returned (masked).
- **Public paths**: `/actuator/{health,info,prometheus}` only; all else
  JWT or `denyAll()`. (No v1 webhook surface — unlike scm; finance has no
  external callback in v1.)

## REST endpoints (v1)

All under `/api/finance/**` (gateway, when introduced, rewrites
`/api/v1/finance/**`). Formal shapes →
[`account-api.md`](../../contracts/http/account-api.md).

| Method | Path | Auth | Idempotency | Use case |
|---|---|---|---|---|
| `POST` | `/api/finance/accounts` | JWT | required | open account (`PENDING_KYC`) |
| `GET` | `/api/finance/accounts/{id}` | JWT | n/a | fetch account + balances |
| `POST` | `/api/finance/accounts/{id}/kyc/upgrade` | JWT (operator) | required | KYC level upgrade → may `ACTIVE` |
| `GET` | `/api/finance/accounts/{id}/balances` | JWT | n/a | available/ledger/held per currency |
| `POST` | `/api/finance/accounts/{id}/holds` | JWT | required | place a hold |
| `POST` | `/api/finance/accounts/{id}/holds/{holdId}/capture` | JWT | required | capture (full/partial) |
| `POST` | `/api/finance/accounts/{id}/holds/{holdId}/release` | JWT | required | release a hold |
| `POST` | `/api/finance/accounts/{id}/transfers` | JWT | required | atomic transfer to another finance account |
| `GET` | `/api/finance/accounts/{id}/transactions` | JWT | n/a | paginated transaction history |
| `GET` | `/actuator/{health,info}` | none | n/a | probes / build info |
| `GET` | `/actuator/prometheus` | network-isolated | n/a | metrics scrape (internal only) |

## fintech Mandatory Rule mapping (rules/domains/fintech.md)

| Rule | Status | Mechanism |
|---|---|---|
| **F1** Fund ops idempotent + Tx-protected | ✅ | `Idempotency-Key` required; balance+txn-state+outbox in one `@Transactional`; no partial fund state |
| **F2** Available/ledger split; single balance writer; (v2) double-entry | ✅ v1 / forward-decl | `Balance` VO `available = ledger − held`; one mutation path; `LEDGER_*` codes forward-declared for ledger-service v2 |
| **F3** Settled txn immutable; reversal-only | ✅ | `TransactionStatusMachine` blocks post-SETTLED mutation; `REVERSAL` txn references original; both audited |
| **F4** KYC/AML gate precedes fund movement | ✅ | un-bypassable `KycGate` + `CompliancePort` in the single application path; `SANCTION_HIT` → operator queue |
| **F5** Money = minor-units / BigDecimal, no float | ✅ | `Money(long minorUnits, Currency)`; JSON as string/integer; `CURRENCY_MISMATCH` guard; grep-zero float/double in `domain/money` |
| **F6** Immutable audit on fund/regulatory ops | ✅ | `audit_log` + `account_status_history` append-only, same Tx (audit-heavy trait) |
| **F7** Regulated PII encrypted + masked | ✅ | `PiiEncryptor` AES-GCM; log/event/error masking (regulated trait) |
| **F8** Reconciliation no auto-close | ✅ (modelled) | `reconciliation_discrepancy` + operator queue; v1 no external source, real matching v2 |

## Trait Rule mapping (rules/traits/)

| Trait Rule | Status | Mechanism |
|---|---|---|
| **transactional** T1 idempotency on mutations | ✅ | `Idempotency-Key` + `idempotency_keys` + Redis primary |
| T2 atomic state-change + outbox | ✅ | publisher write inside use-case `@Transactional` |
| T3 outbox table + polling relay | ✅ | v2 `account_outbox` (`AbstractOutboxPublisher` — `AccountOutboxPublisher` relay); retained-unused `outbox`/`processed_events` stubs |
| T4 state machine via dedicated module | ✅ | `AccountStatusMachine` / `TransactionStatusMachine`, no setter mutation |
| T7 optimistic locking on aggregates | ✅ | `@Version` on `Account`, `Balance`, `Transaction` |
| **regulated** | ✅ | KYC/AML gate (F4), PII encryption + masking (F7), operator-review queue (no auto-clear); `rules/traits/regulated.md` loaded |
| **audit-heavy** | ✅ | append-only `audit_log` actor/time/before/after; immutable history; `rules/traits/audit-heavy.md` loaded |
| integration-heavy | N/A v1 (declared off) | No real external adapter v1; fintech F1 still mandates idempotency/CB on the v2 external port — documented, enforced when real adapter lands |

## Dependencies

| Dir | Target | Protocol | Notes |
|---|---|---|---|
| In | finance `gateway-service` (v1 deferred) → direct JWT until then | HTTP `/api/finance/**` | tenant-validated JWT |
| Out | MySQL `finance_db` | JDBC | accounts, account_status_history, balances, holds, transactions, audit_log, compliance_review_queue, reconciliation_discrepancy, account_outbox (v2), idempotency_keys; retained-unused outbox + processed_events stubs |
| Out | Redis | TCP | idempotency primary (SET NX-EX) |
| Out | Kafka | TCP | `finance.{account,balance,transaction,compliance}.*.v1`; `acks=all`, `enable.idempotence=true` |
| Out | IAM `/oauth2/jwks` | HTTPS | RS256 JWT verification (libs/java-security) |
| Out (obs) | OTLP collector | HTTPS | `${OTLP_ENDPOINT}` traces |

No cross-service master-event consumption in v1 (account-service is a leaf).

## Observability

- Logback MDC `traceId / requestId / tenantId / accountId` (libs/java-observability;
  pattern already in skeleton `application.yml`).
- Outbox v2 metrics (libs `MicrometerOutboxMetrics`, prefix `account`):
  `account.outbox.publish.success.total` / `account.outbox.publish.failure.total`
  (eventType-tagged), `account.outbox.lag.seconds`, `account.outbox.pending.count`
  gauge. (Replaces the v1 bespoke `account_outbox_publish_failures_total` counter
  — renamed; TASK-FIN-BE-045.)
- Counters: `compliance_sanction_hits_total`, `fund_movement_rejected_total{reason}`.
- Tracing OTLP via `micrometer-tracing-bridge-otel`; sampling 1.0 (dev).
- `/actuator/prometheus` internal docker network only.

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Missing `Idempotency-Key` on mutation | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 2 | Same key, different payload | 409 `IDEMPOTENCY_KEY_CONFLICT` |
| 3 | Cross-tenant JWT — `tenant_id ∉ {finance,*}` **and** signed `entitled_domains ∌ finance` (dual-accept both branches fail) | 403 `TENANT_FORBIDDEN` |
| 4 | Redis offline during idempotency check | fail-CLOSED → `idempotency_keys` table; both down → 503 `IDEMPOTENCY_STORE_UNAVAILABLE` |
| 5 | Fund move on non-ACTIVE account | 409 `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_FROZEN` |
| 6 | `available < amount` | 422 `INSUFFICIENT_AVAILABLE_BALANCE` |
| 7 | Capture/release unknown or settled hold | 409 `HOLD_NOT_FOUND` / `HOLD_ALREADY_SETTLED` |
| 8 | Mutating a SETTLED/COMPLETED txn | 409 `TRANSACTION_ALREADY_SETTLED` (reversal-only) |
| 9 | KYC level insufficient | 403 `KYC_REQUIRED` / `KYC_LEVEL_INSUFFICIENT` / 422 `TRANSACTION_LIMIT_EXCEEDED` |
| 10 | Sanction hit | 422 `SANCTION_HIT`; txn `FAILED`; operator-queue row; event emitted |
| 11 | Cross-currency operation | 422 `CURRENCY_MISMATCH` |
| 12 | Amount ≤ 0 / scale violation | 422 `AMOUNT_INVALID` |
| 13 | Account status transition not allowed | 409 `ACCOUNT_STATUS_TRANSITION_INVALID` |
| 14 | Optimistic-lock conflict | 409 `CONCURRENT_MODIFICATION` |
| 15 | Crypto key misconfiguration | context shutdown at startup (boot self-test) |
| 16 | Outbox publish failure | row stays `PENDING`, retried next tick; counter increments |

## Testing Strategy

- **Unit** (`:account-service:test`): domain — `AccountStatusMachineTest`,
  `TransactionStatusMachineTest`, `MoneyTest` (no float, scale, currency
  mismatch), `BalanceTest` (available invariant, hold/capture/release math),
  `KycGateTest`; application — `AccountApplicationServiceTest` (mock ports,
  STRICT_STUBS); adapters — validator unit tests, `PiiEncryptorTest`
  (round-trip + tamper), `TenantClaimEnforcerTest`.
- **Slice**: JPA adapter slices, `@WebMvcTest` + SecurityConfig +
  `GlobalExceptionHandler` error-envelope.
- **Integration** (`:account-service:integrationTest`, `@Tag("integration")`,
  Testcontainers MySQL+Redis + WireMock JWKS — **H2 forbidden**): account
  open→KYC→active; hold→capture/release; transfer atomicity; **same
  Idempotency-Key concurrent re-request → funds move once**; cross-tenant
  JWT → 403; sanction-hit → FAILED + operator queue; SETTLED immutability +
  reversal; audit_log append-only; optimistic-lock concurrency.
  `integrationTest` excluded from `./gradlew check` (Docker-free fast loop).

## Required Artifacts mapping (rules/domains/fintech.md § Required Artifacts)

| # | Artifact | Disposition |
|---|---|---|
| 1 | Account state diagram | **Inlined** here (§ Account State Machine) — scm-procurement precedent (dedicated file = low-priority follow-up if it grows) |
| 2 | Transaction state diagram | **Inlined** (§ Transaction State Machine) |
| 3 | Balance model | **Inlined** (§ Balance Model) |
| 4 | KYC/AML compliance-gate flow | **Inlined** (§ KYC/AML Compliance Gate) |
| 5 | Reconciliation flow | **Inlined, forward-declared** (§ Reconciliation) — real flow = v2 |
| 6 | Ledger / double-entry model | **Deferred** — `ledger-service` v2 (ADR-MONO-008 § D3) |
| 7 | Error-code registration | This spec PR adds fintech codes to `platform/error-handling.md` |
| 8 | Bounded-context map | v1 single deployable; context split → `PROJECT.md` Service Map v2 |

## References

- `platform/architecture-decision-rule.md`, `platform/service-types/INDEX.md`,
  `platform/service-types/rest-api.md`, `platform/error-handling.md`,
  `platform/testing-strategy.md`
- `rules/domains/fintech.md` (F1–F8 — governing), `rules/traits/transactional.md`,
  `rules/traits/regulated.md`, `rules/traits/audit-heavy.md`
- `projects/finance-platform/PROJECT.md`,
  [`iam-integration.md`](../../integration/iam-integration.md)
- [`account-api.md`](../../contracts/http/account-api.md) (this PR),
  [`finance-account-events.md`](../../contracts/events/finance-account-events.md) (this PR)
- precedent: `projects/scm-platform/specs/services/procurement-service/architecture.md`
  (Hexagonal canonical-form shape reference; TASK-FIN-BE-001 § Related Specs)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D2/D3 (v1 =
  account-service; ledger = v2), `docs/adr/ADR-MONO-013` §3.3 (backend-only)
- TASK-MONO-114 — bootstrap (skeleton + IAM V0017), TASK-FIN-BE-001 — this
  spec + impl task
