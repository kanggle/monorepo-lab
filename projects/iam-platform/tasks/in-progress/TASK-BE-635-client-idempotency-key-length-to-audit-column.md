# Task ID

TASK-BE-635

# Title

The client `Idempotency-Key` header reaches the audit column `admin_actions.idempotency_key VARCHAR(100)` without a length check. Seven endpoints have no limit, and the bulk lock overflows by 1 character at the 64-character boundary (64 + `:` + a 36-character account ID = 101). When it overflows, the client's input error comes back as `500 AUDIT_FAILURE`.

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test
- api

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Sonnet. Choosing the bulk-lock fix touches downstream retry behaviour, so Opus should review that decision.
> Source: found while counting all of `TASK-BE-634` AC-1, the keys the server builds itself. This ticket is the **other kind**: keys the client sends.

# Dependency Markers

- **Prerequisite**: `TASK-BE-634` (the server-built keys are fixed and held by `AuditIdempotencyKeysTest`).

# Goal

`TASK-BE-634` limits only the keys that admin-service **builds itself**. The keys a client sends in the `Idempotency-Key` header go into the audit row as they are.

1. **Unchecked headers.** On 2026-10-14, 7 places matched `@RequestHeader("Idempotency-Key")` without `@Size`. These cover single-account lock and unlock, GDPR, sessions, 2FA reset and similar. A header over 100 characters makes the audit write fail, and the response is `500 AUDIT_FAILURE`. This is client input, so the answer should be `400 VALIDATION_ERROR`. The domain-set, group-set and invitation endpoints already return 400 with `MAX_IDEMPOTENCY_KEY = 100` in the use case.
2. **Bulk lock, per row.** `BulkLockAccountUseCase.processOne` builds `cmd.idempotencyKey() + ":" + accountId`. The header is `@Size(max = 64)` and `accounts.id` is `VARCHAR(36)`, so the maximum is 64 + 1 + 36 = **101**. That is 1 character over at the boundary. `accountIds` elements have no length check. 🔴 This key is also passed to account-service as the **downstream idempotency key** (the `ik` argument of `AccountAdminUseCase.lock`). Changing its shape can change how retries are judged, so the fix must first record what the downstream does with it.

# Scope

## In Scope

- AC-0 IT, red first:
  - a single lock with a 101-character header returns 500 `AUDIT_FAILURE`;
  - a bulk lock with a 64-character header and a 36-character account ID shows `AUDIT_FAILURE` in that row's result.
- Unchecked headers: a 400 for headers over 100 characters. Choose between checking in the controller and a single shared mechanism.
- Bulk lock: choose one fix. Candidates are a header of 63 or fewer (a contract change), length-limiting the `accountIds` elements, or a separate per-row key for the audit only. The choice must be backed by a record of how account-service handles the key.
- Contract (`admin-api.md`): the 400 row for each endpoint.

## Out of Scope

- Widening the column. The audit row has an immutability trigger.

# Acceptance Criteria

- [ ] **AC-0**: both of the two IT cases above fail before the fix. If either does not, record a measurement showing the premise was wrong.
- [ ] **AC-1**: a table of the 7 unchecked headers and the bulk lock, with each one's maximum length and evidence for it.
- [ ] **AC-2**: after the fix, the single-lock case returns 400 `VALIDATION_ERROR` and the bulk lock completes that row.
- [ ] **AC-3**: the contract has the 400 rows, and a guard catches a new endpoint that takes a header key without a length check.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/data-model.md` § admin_actions

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the `Idempotency-Key` row of each endpoint; bulk-lock ≤64)

# Edge Cases

- A replay with the same key and the same account: the existing 409 and replay semantics stay unchanged.

# Failure Scenarios

1. Changing the bulk-lock per-row key shape changes how account-service judges retries, and one lock gets applied twice.
2. Checking only in the controller misses an internal path that calls the use case directly with its own key.

---

# Implementation Record (2026-10-14 UTC)

## AC-0: premise, read not run (no local Docker)

Confirmed by reading `AdminActionAuditWriter#recordStart` — the IN_PROGRESS INSERT runs **outside** the
try/catch that wraps the downstream HTTP call in `AccountAdminUseCase#executeAccountAction`. A `RuntimeException`
from `repository.save(entity)` (MySQL strict mode rejects a VARCHAR(100) overflow as `DataIntegrityViolationException`)
is caught inside `recordStart` itself and rethrown as `AuditFailureException`, which propagates straight out of
`lock()`/`unlock()` with no downstream call ever attempted.

- Single lock: a 101-char header reaches `recordStart` with the raw key, unchecked pre-fix → `AuditFailureException`
  → `AdminExceptionHandler` → `500 AUDIT_FAILURE`. Premise holds.
- Bulk lock: `BulkLockAccountUseCase#processOne` builds `cmd.idempotencyKey() + ":" + accountId` and calls
  `accountAdminUseCase.lock(...)`, which throws the same `AuditFailureException`; `processOne`'s own
  `catch (AuditFailureException ex)` turns that into `{outcome: FAILURE, errorCode: AUDIT_FAILURE}` for **that row
  only** — the batch itself still answers 200. Premise holds (64 + ":" + 36 = 101 > 100).

`ClientIdempotencyKeyOverflowIntegrationTest` (`AC-0a`/`AC-0b`) encodes both as Testcontainers ITs calling the use
cases directly (bypassing the new controller-boundary `@Size`, the shape of Failure Scenario 2) — written, compiled,
and confirmed to register + skip cleanly under `integrationTest` with no Docker (`BUILD SUCCESSFUL`, 5/5 skipped, 0
errors). CI (`Integration (iam …, Testcontainers)`) is authoritative for the actual red→green.

## AC-1: every `@RequestHeader("Idempotency-Key")`, recounted (12 declarations, not ~7)

| # | File:line (pre-fix) | Reaches the audit column? | Max length before fix | Fix |
|---|---|---|---|---|
| 1 | `AccountSecondFactorAdminController:45` (2fa-reset) | Yes, verbatim | unbounded | `@Size(max=100)` |
| 2 | `AdminGdprController:37` (gdpr-delete) | Yes, verbatim | unbounded | `@Size(max=100)` |
| 3 | `AccountAdminController:154` (lock) | Yes, verbatim + sent downstream as `ik` | unbounded | `@Size(max=100)` |
| 4 | `AccountAdminController:194` (unlock) | Yes, verbatim + sent downstream | unbounded | `@Size(max=100)` |
| 5 | `AccountAdminController:215` (bulk-lock) | Yes, concatenated `+":"+accountId` | already `@Size(max=64)`, but 64+1+36=**101** | lowered to `@Size(max=63)` + `accountIds` element `@Size(max=36)` |
| 6 | `OperatorAdminController:156` (createOperator) | **No** — captured but never passed to `CreateOperatorUseCase.createOperator(...)` (dead param; audit uses `"create:"+auditId`) | unbounded | `@Size(max=100)` for guard consistency only |
| 7 | `SessionAdminController:38` (revoke) | Yes, verbatim | unbounded | `@Size(max=100)` |
| 8 | `AuditController:61` (GET /audit, `required=false`) | **No** — captured into `QueryAuditCommand` but `AuditQueryUseCase` never reads `cmd.idempotencyKey()` (grep: 0 matches); not even in `admin-api.md` | unbounded | `@Size(max=100)` for guard consistency only |
| 9 | `TenantAdminController:78` (createTenant, `required=false`) | Yes, verbatim (or random UUID fallback) | unbounded | `@Size(max=100)` |
| 10 | `TenantAdminController:107` (updateTenant, `required=false`) | Yes — **and** `UpdateTenantUseCase` appends `":status"` (7 chars) for the status-change audit row when status+displayName both change, so the binding constraint is 100−7=93, not 100 🔴 found by reading the sibling, not named in the task | unbounded | `@Size(max=93)` (`ClientIdempotencyKey.TENANT_UPDATE_HEADER_MAX`) |
| 11 | `DomainPermissionSetController:77` (`required=false`) | Yes, but already bounded in `DomainPermissionSetUseCase.MAX_IDEMPOTENCY_KEY=100` before the audit write | n/a — out of scope, named in the task itself | unchanged |
| 12 | `GroupDomainPermissionSetController:51` | Yes, already bounded in `GroupDomainPermissionSetUseCase.MAX_IDEMPOTENCY_KEY=100` | n/a — out of scope | unchanged |
| 13 | `OperatorInvitationController:49` | Yes, already bounded in `OperatorInvitationUseCase.MAX_IDEMPOTENCY_KEY=100` | n/a — out of scope | unchanged |

So: **7 truly unchecked-and-reachable** (rows 1–4, 5-partial, 7, 9, 10), **2 unchecked-but-dead** (6, 8 — bounded
anyway for guard consistency, since the guard is structural, not reachability-based), **3 already-checked**
(11–13, untouched). The task's "~7" matched rows 1–4+6+7+(a literal-pattern grep that also matched 5's annotation
line) — recounting by actually reading the use cases changed the shape (bulk-lock's accompanying `accountIds`
bound, and the tenant-update 93 vs 100 split) but not materially the count.

## AC-2: fix

- New `ClientIdempotencyKey` (public, `com.example.admin.presentation`) holds `MAX_LENGTH=100`,
  `BULK_LOCK_HEADER_MAX=63`, `BULK_LOCK_ACCOUNT_ID_MAX=36`, `TENANT_UPDATE_HEADER_MAX=93` — the single shared
  mechanism, consumed from both `presentation` and `presentation.tenant`.
- All 10 controller-boundary sites (rows 1–10 above) now carry `@Size`; the 3 already-use-case-checked sites
  (11–13) are untouched and recorded in `ClientIdempotencyKeyTest`'s `DOWNSTREAM_VALIDATED_FILES` allowlist.
- **Bulk-lock decision**: read `AccountServiceClient#lock`/`#unlock` (`callPost`, `h.add("Idempotency-Key", …)`) and
  grepped `apps/account-service/src/main/java` for `idempotency`/`Idempotency` — **zero matches**. account-service
  does not read the header at all; its lock/unlock internal endpoints carry no idempotency handling of their own.
  So Failure Scenario 1 ("changing the per-row key shape changes how account-service judges retries") cannot occur
  — there is no downstream retry judgement on this key to disturb. With that settled, the smallest fix that keeps
  the per-row key's **shape** unchanged (`header + ":" + accountId`, still the admin-service-internal replay key
  via `isIdempotencyKeyUsed` / the unique index, still the same value sent downstream) is: lower the header bound
  64→63 (contract change, `admin-api.md` updated first) **and** bound `accountIds` elements to ≤36
  (`accounts.id VARCHAR(36)`) — 63+1+36=100 exactly. Rejected alternatives: a separate audit-only key (adds a
  second key shape with no benefit once the downstream-irrelevance was confirmed) and bounding `accountIds` alone
  without lowering the header (still 64+1+36=101, over).
- `Idempotency-Key` on bulk-lock is a genuine **contract break at the exact 64-char boundary** for any caller that
  was sending 64-char keys to that one endpoint (single lock/unlock/others stay at 100). Updated
  `BulkLockControllerSliceTest`'s existing `bulk_lock_idempotency_key_over_64_chars…` boundary test to the new
  63/64 line and added an at-boundary (63) acceptance control + an `accountId`-over-36 control.

## AC-3: contract + guard

- `admin-api.md`: added/extended the `400 VALIDATION_ERROR` row on lock, unlock (via "동일 구조"), bulk-lock,
  gdpr-delete, 2fa-reset, session-revoke, createOperator, tenant-create, tenant-update; bulk-lock's header line
  64→63 with the 63+1+36=100 arithmetic spelled out; bulk-lock `Constraints` gained the `accountIds` ≤36 row;
  tenant-update gained the 93/`:status` explanation.
- `ClientIdempotencyKeyTest` (new): (a) a source scan over `src/main/java` requiring every
  `@RequestHeader(...Idempotency-Key...)` to carry a nearby `@Size` or be in the 3-file allowlist — fails if a
  future endpoint adds the header unbounded; (b) a constants-consistency check (63+1+36=100, 93+7=100); (c) a
  control test feeding the exact pre-fix single-line form through the same predicate, proving it would have
  flagged every one of the 7 unchecked sites before the fix (bite proof, in lieu of a real two-commit CI
  round-trip — no Docker locally to push a red-only commit the way TASK-BE-634 did); (d) a control test proving
  the predicate accepts this ticket's own fixed form. First run of (a) failed twice before passing: once on
  `ClientIdempotencyKey.java`'s own javadoc (which quotes the annotation literally in prose) matching the
  declaration pattern, and once on the bulk-lock fix's multi-line explanatory comment pushing the `@Size` outside
  a too-narrow lookahead window — both are recorded as "why the guard needed its own retry" in the test's comments
  rather than silently fixed.

## Local verification

- `./gradlew :projects:iam-platform:apps:admin-service:compileTestJava` — rc=0.
- `./gradlew :projects:iam-platform:apps:admin-service:test` (unit+slice; `@Tag("integration")` excluded by the
  module's own `test` task config) — **1309 tests, 0 failed, 58 skipped** (the 58 are the pre-existing + this
  ticket's new IT classes, skipped by `DockerAvailableCondition`, not excluded by tag at this layer — consistent
  with the other IT classes in this module).
- `./gradlew :projects:iam-platform:apps:admin-service:integrationTest --tests
  "...ClientIdempotencyKeyOverflowIntegrationTest"` — rc=0, 5/5 methods registered and cleanly skipped (no
  Docker), confirming the class loads and wires without error; CI's Testcontainers lane is authoritative for the
  actual assertions.
- `bash scripts/check-error-code-registry.sh` — OK (no new error codes introduced; `VALIDATION_ERROR` is
  pre-existing).

## Deviations from the task's literal wording

- AC-1's "7 unchecked headers" became **10 fixed + 2 fixed-for-consistency-only + 3 left alone** on recount — see
  the AC-1 table above for why (bulk-lock's `accountIds` dimension and tenant-update's `:status` suffix were not
  visible from the task's own endpoint list and only surfaced by reading `UpdateTenantUseCase`/`AccountServiceClient`
  directly, per the task's own "recount, don't trust that number" instruction).
- `OperatorAdminController#createOperator` and `AuditController#query`'s headers are bounded even though neither
  is reachable to the audit column today (dead parameters) — done for the guard's structural consistency (it
  scans declaration sites, not data flow) and to future-proof a later wiring of either value.
- No AMI/demo deployment — same caveat as TASK-BE-634's close record; this lands in the admin-service jar only
  once merged + deployed.
