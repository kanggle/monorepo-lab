# Task ID

TASK-BE-635

# Title

The client `Idempotency-Key` header reaches the audit column `admin_actions.idempotency_key VARCHAR(100)` without a length check. Seven endpoints have no limit, and the bulk lock overflows by 1 character at the 64-character boundary (64 + `:` + a 36-character account ID = 101). When it overflows, the client's input error comes back as `500 AUDIT_FAILURE`.

# Status

ready

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
