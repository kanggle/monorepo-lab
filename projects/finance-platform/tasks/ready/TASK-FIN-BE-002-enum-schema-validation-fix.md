# Task ID

TASK-FIN-BE-002

# Title

account-service Hibernate enum schema-validation mismatch fix — `@Enumerated(EnumType.STRING)` ↔ Flyway `CHAR/VARCHAR` (Fix issue found in TASK-FIN-BE-001)

# Status

ready

# Owner

backend

# Task Tags

- code
- bug

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

**Fix issue found in TASK-FIN-BE-001** (surfaced by the **TASK-MONO-115** `finance-integration-tests` CI job — run [`26034213923`](https://github.com/kanggle/monorepo-lab/actions/runs/26034213923/job/76528155098), `11 tests completed, 11 failed`).

Hibernate 6.x + `MySQLDialect` maps a `@Enumerated(EnumType.STRING)` field to a **native MySQL `ENUM(...)` column type for schema-validation**. The Flyway `V1__init.sql` declares those columns as `CHAR(3)` / `VARCHAR(n)` (with `CHECK ... IN (...)` constraints — the portable, append-only-friendly pattern). With `hibernate.ddl-auto: validate`, the first mapped enum column hit (`accounts.currency`) raises:

```
org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation:
wrong column type encountered in column [currency] in table [accounts];
found [char (Types#CHAR)], but expecting [enum ('eur','jpy','krw','usd') (Types#ENUM)]
```

→ `entityManagerFactory` bean fails → `ApplicationContext` fails to load → **all 4 Testcontainers IT classes (11 tests) fail on context load** (`ApplicationContext failure threshold (1) exceeded` cascade).

This is **systemic, single-pattern**: schema-validation bailed at the first column (`accounts.currency`) but every `@Enumerated(EnumType.STRING)` field carries the same latent mismatch — `Account.status`/`kyc_level`, `Balance.currency`, `Hold.currency`/`status`, `Transaction.type`/`status`/`currency`, `AuditLog.actor_type`, `AccountStatusHistory.from_status`/`to_status`/`actor_type`, etc. The Flyway DDL is **correct and spec-compliant** (architecture.md fintech F5: "integer minor-unit money (BIGINT) + ISO currency CHAR(3)"; status columns are `VARCHAR` + `CHECK`); the defect is in the **entity / Hibernate mapping config**, not the schema.

Why TASK-FIN-BE-001's gates did not catch it (no green-wash — recorded honestly at close): `:check` (117 unit/slice) never boots real MySQL + Flyway + Hibernate schema-validation; the IT were never executed (local Docker blocker; dispatcher BE-301 verified F1–F8 *structurally* via grep/diff, not by running the IT). The TASK-FIN-BE-001 close one-liner explicitly stated "behavioural proof is not CI-run" and named TASK-MONO-115 as the follow-up that would prove it — which it now has.

---

# Scope

## In Scope

1. **Make every `@Enumerated(EnumType.STRING)` column validate against the Flyway `VARCHAR`/`CHAR` type.** Preferred minimal systemic fix: set the Hibernate property

   ```
   spring.jpa.properties.hibernate.type.preferred_enum_jdbc_type: VARCHAR
   ```

   (Hibernate 6.2+) in account-service `application.yml` so **all** mapped enums validate/generate as `VARCHAR` instead of native MySQL `ENUM`. Confirm it applies to the IT runtime (the `test` profile / `@DynamicPropertySource` path — see Edge Case 4). If the global property is not honoured on the resolved config path, fall back to per-field `@JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)` on **every** `@Enumerated(EnumType.STRING)` field across all entities (mirrors the existing `@JdbcTypeCode(SqlTypes.JSON)` precedent already used for `AuditLog.before_state/after_state`).
2. Verify **all 4 IT classes / 11 tests** green on **real MySQL + Redis + Kafka** (`AccountLifecycleIntegrationTest`, `AuditAndImmutabilityIntegrationTest`, `CrossTenantHttpIntegrationTest`, `IdempotencyConcurrencyIntegrationTest`) — via the TASK-MONO-115 `finance-integration-tests` CI job (authoritative; local Testcontainers if the Docker blocker permits).
3. Re-run `:projects:finance-platform:apps:account-service:check` — must stay **117 / 0 fail / 0 error / 0 skip** (no unit/slice regression).

## Out of Scope

- **No change to `V1__init.sql`** — append-only (F6) and spec-compliant. The DDL `CHAR(3)`/`VARCHAR` columns are correct; do **not** convert them to native `ENUM(...)` (brittle, migration-hostile, fights the existing `CHECK ... IN` pattern).
- **No new V2 migration** — this is a mapping-config defect, not a schema defect; there is nothing to migrate.
- **No domain logic / API contract / event contract / `architecture.md` / ADR change** — runtime divergence between impl mapping and the (correct) spec'd schema only.
- **No change to the TASK-MONO-115 `finance-integration-tests` ci.yml job** — it is correct as-is (it did exactly its job: surfaced this bug in 1m16s).

---

# Acceptance Criteria

1. `finance-integration-tests` CI job **green**: 4 IT classes / **11 tests pass** on real MySQL+Redis+Kafka (cite the green CI run id in the impl PR).
2. `:projects:finance-platform:apps:account-service:check` stays **117/0/0/0** (no unit/slice regression).
3. Diff scope = entity annotations and/or `application.yml` (+ test config if needed) **only**. **No** `V1__init.sql` change, **no** new migration, **no** domain/contract/`architecture.md`/ADR change.
4. Fix is **systemic** — covers **all** `@Enumerated(EnumType.STRING)` columns, not just `accounts.currency` (proven by the IT booting the full schema: Hibernate `validate` checks every mapped column, so a green context-load = every enum column reconciled).
5. Goal cites **TASK-FIN-BE-001** + the surfacing CI run (project INDEX Review Rule satisfied).

---

# Related Specs

- [TASK-FIN-BE-001](../done/TASK-FIN-BE-001-account-service-bootstrap.md) — original task (the impl this fixes; in `done/`, bug surfaced post-close)
- [TASK-MONO-115](../../../../tasks/ready/TASK-MONO-115-finance-integration-ci-job.md) — the CI job that surfaced this (root task; honest-gap-closure chain)
- [specs/services/account-service/architecture.md](../../specs/services/account-service/architecture.md) — MySQL + Flyway + `ddl-auto: validate` + fintech F5 `CHAR(3)` currency (**unchanged** — the schema is correct)
- [platform/testing-strategy.md](../../../../platform/testing-strategy.md) — Testcontainers IT = behavioural authority (H2 forbidden)

---

# Related Contracts

- None (no API / event contract change — mapping/config fix only).

---

# Target Service / Component

- `projects/finance-platform/apps/account-service` — JPA entities (`domain/**` with `@Entity`) and/or `src/main/resources/application.yml` (+ test profile config).

---

# Edge Cases

1. **Global property vs per-field**: `hibernate.type.preferred_enum_jdbc_type=VARCHAR` is the minimal systemic fix, but verify it is actually honoured on the IT's resolved Hibernate config (some setups resolve dialect/type-contributor ordering differently). If any enum column still validates as `ENUM`, fall back to explicit per-field `@JdbcTypeCode(SqlTypes.VARCHAR)` — and apply it to **every** `@Enumerated(EnumType.STRING)` field (grep-enumerate first; do not fix only `currency`).
2. **JSON columns unaffected**: `AuditLog.before_state/after_state` already use `@JdbcTypeCode(SqlTypes.JSON)` against `JSON` columns — correct; do not touch. Only `@Enumerated(EnumType.STRING)` ↔ `CHAR/VARCHAR` columns are in scope.
3. **No ORDINAL / converter enums expected**: all enum fields are `EnumType.STRING`; grep-verify there is no `@Enumerated(EnumType.ORDINAL)` or `@Convert` enum that needs separate handling.
4. **Test profile config path**: the IT use `@DynamicPropertySource` (`AbstractAccountIntegrationTest`) to wire the MySQL container; confirm the fix (property or annotation) is on a path that the `test` profile actually loads — a property placed only in a prod-only profile would leave the IT still red.

---

# Failure Scenarios

## A. Global property insufficient on some Hibernate 6 path

→ Apply per-field `@JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)` to **every** `@Enumerated(EnumType.STRING)` field (Account/Balance/Hold/Transaction/AuditLog/AccountStatusHistory + any others — enumerate by grep, fix all, not just the first). Re-verify via the CI job.

## B. IT still red after the enum fix for a different reason

→ The context-load failure may have masked a second latent defect (schema-validation stops at the first error). Diagnose the next surfaced `SchemaManagementException` / bean-creation error from the CI log; if it is another column-type/mapping mismatch, fix it within this task (same class of defect). If it is a genuinely different defect class (logic/behaviour), STOP and surface a separate follow-up fix-task (do not silently expand scope or green-wash).

## C. Local Docker blocker prevents local IT verification

→ The TASK-MONO-115 `finance-integration-tests` CI job is the authoritative behavioural proof (Linux runner, real MySQL+Redis+Kafka). The impl PR's own finance code change triggers the `finance` path-filter → the job runs on the PR; cite the green job/run id in the PR description and AC-1.

---

# Test Requirements

- `finance-integration-tests` CI job: 4 IT / 11 tests PASS (cite run id).
- `:account-service:check`: 117/0/0/0 (no regression).
- Diff scope assertion in the impl PR (no V1/migration/contract/spec/ADR touched).

---

# Definition of Done

- [ ] All `@Enumerated(EnumType.STRING)` columns validate against Flyway `VARCHAR/CHAR` (systemic fix applied)
- [ ] `finance-integration-tests` CI job green (4 IT / 11 tests) — run id cited
- [ ] `:account-service:check` 117/0/0/0
- [ ] Diff scope = entity/config only (no V1/migration/contract/architecture.md/ADR)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — a well-understood Hibernate-6 + MySQL `@Enumerated(EnumType.STRING)` → native-`ENUM` schema-validation gotcha; complexity is *breadth* (one property or a uniform per-field annotation across ~7 entities) + IT-green verification, not novel domain design.
- **Critical verification discipline**: `:check` green is **necessary but NOT sufficient** — `:check` (Docker-free unit/slice) is exactly the gate that hid this bug (it never boots real MySQL + Hibernate `validate`). The fix is only proven by the **TASK-MONO-115 CI job** (real MySQL). Do not declare done on `:check` alone (green-wash prohibited).
- **dependency**:
  - `선행`: **TASK-MONO-115 impl PR #601 merged** — the `finance-integration-tests` job must exist on `main` so this fix's impl PR can be verified by it (and so `main`'s finance IT signal flips red→green when this merges).
  - `후속`: none. Merging this fix = `main`'s `finance-integration-tests` job goes **green**; closes finance v1's surfaced behavioural-proof gap.
- **green-wash chain (do not break)**: TASK-FIN-BE-001 close honestly surfaced the unverified-IT gap → TASK-MONO-115 built the CI job → the job proved the concrete bug → **this task fixes it**. The bug was never silently dropped; it was tracked end-to-end. Failure Scenario A of TASK-MONO-115 pre-prescribed exactly this fix-task.
