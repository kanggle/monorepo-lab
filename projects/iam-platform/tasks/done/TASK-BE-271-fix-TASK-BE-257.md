# Task ID

TASK-BE-271

# Title

Fix TASK-BE-257 — readOnly tx on audit write + wrong StatusChangeReason + 1001-item error code

# Status

ready

# Owner

backend

# Task Tags

- code

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

Three issues found during review of TASK-BE-257 (bulk provisioning API):

1. **`@Transactional(readOnly = true)` with write (Critical)**: `BulkProvisionAccountUseCase.execute()` is
   annotated `@Transactional(readOnly = true)`, but at the end of the method it calls
   `historyRepository.save(entry)` to write the bulk audit row. The per-row `REQUIRES_NEW`
   sub-transactions (in `RowProvisioningHelper`) correctly operate in independent transactions,
   but the audit write runs inside the outer `readOnly=true` transaction. With a strict JDBC
   connection pool or MySQL `read_only` session variable, this write will fail silently or throw
   a `TransactionSystemException`, causing the audit row to be lost or the entire bulk call to
   fail at the last step. The correct annotation is `@Transactional` (default `REQUIRED`, not
   `readOnly`). Because the outer transaction does not perform any entity loads that benefit from
   the read-only hint (tenant lookup is the only read, and it occurs before the loop), the
   `readOnly` hint provides no optimization benefit here.

2. **Wrong `StatusChangeReason` enum value in audit entry (Critical)**: The spec
   (`account-internal-provisioning.md` §Audit Action Codes) says bulk create should record
   `OPERATOR_PROVISIONING_CREATE` as the reason code. However:
   (a) There is no `OPERATOR_PROVISIONING_CREATE` value in the `StatusChangeReason` enum.
   (b) The implementation uses `OPERATOR_PROVISIONING_STATUS_CHANGE`, which is the reason for
       a *status change* operation, not an account creation.
   Fix: add `OPERATOR_PROVISIONING_CREATE` to `StatusChangeReason` and use it in
   `BulkProvisionAccountUseCase.writeBulkAuditEntry()`.

3. **1001-item HTTP error code mismatch (Warning)**: The contract spec
   (`account-internal-provisioning.md` §Errors) and task AC both specify that items > 1 000
   returns 400 `BULK_LIMIT_EXCEEDED`. At the HTTP boundary, however, the `@Size(max=1000)`
   Bean Validation on `BulkProvisionAccountRequest.items` fires first and returns
   `VALIDATION_ERROR` instead of `BULK_LIMIT_EXCEEDED`. The integration test
   `bulkCreate_1001Items_returns400` currently expects `VALIDATION_ERROR`, contradicting the
   contract. To align with the spec, the `@Size` constraint message should be handled by a
   custom validator or the controller should pre-check the size and throw
   `BulkLimitExceededException` before delegating to the use case. Alternatively, the
   `CommonGlobalExceptionHandler.handleValidation()` can be overridden in `GlobalExceptionHandler`
   to detect the `items` field violation and return `BULK_LIMIT_EXCEEDED`. The chosen approach
   must ensure the HTTP-observable error code matches `BULK_LIMIT_EXCEEDED` for the limit case.
   Update the integration test to assert `BULK_LIMIT_EXCEEDED`.

---

# Scope

## In Scope

- **Fix 1 — `@Transactional` annotation**:
  - Change `@Transactional(readOnly = true)` to `@Transactional` on
    `BulkProvisionAccountUseCase.execute()`.
  - Update the unit test `BulkProvisionAccountUseCaseTest` to verify the audit row
    is written when items are non-empty (already passing, but ensure no `readOnly`
    assumption in mock setup).

- **Fix 2 — `StatusChangeReason.OPERATOR_PROVISIONING_CREATE`**:
  - Add `OPERATOR_PROVISIONING_CREATE` to `StatusChangeReason` enum.
  - Update `BulkProvisionAccountUseCase.writeBulkAuditEntry()` to use
    `StatusChangeReason.OPERATOR_PROVISIONING_CREATE`.
  - Verify no existing tests are broken by the enum addition.

- **Fix 3 — 1001-item error code**:
  - Make the controller (or a dedicated validator) throw `BulkLimitExceededException`
    when `items.size() > 1000` *before* Bean Validation produces `VALIDATION_ERROR`.
    Preferred approach: add an explicit size check in `BulkAccountController.bulkCreate()`
    after mapping items, or override the `@Size` violation handling in `GlobalExceptionHandler`
    for the `items` field.
  - Update `BulkProvisioningIntegrationTest.bulkCreate_1001Items_returns400` to expect
    `BULK_LIMIT_EXCEEDED` (not `VALIDATION_ERROR`).
  - Update `BulkAccountControllerTest` to add a slice test that sends 1001 items and
    expects `BULK_LIMIT_EXCEEDED`.

## Out of Scope

- Changes to `RowProvisioningHelper` — the `REQUIRES_NEW` transaction logic is correct.
- Changes to `BulkAccountController` routing (full-path vs class-level `@RequestMapping`)
  — the workaround is functionally correct.
- Changes to other services.
- Extracting the duplicated `validateTenantScope` helper.

---

# Acceptance Criteria

- [ ] `BulkProvisionAccountUseCase.execute()` annotated `@Transactional` (not `readOnly=true`).
- [ ] `StatusChangeReason` enum contains `OPERATOR_PROVISIONING_CREATE`.
- [ ] `BulkProvisionAccountUseCase.writeBulkAuditEntry()` uses `OPERATOR_PROVISIONING_CREATE`.
- [ ] Integration test `bulkCreate_1001Items_returns400` asserts `code = BULK_LIMIT_EXCEEDED`.
- [ ] Slice test for 1001 items returns `BULK_LIMIT_EXCEEDED` (not `VALIDATION_ERROR`).
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:check` PASS.
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:integrationTest` PASS
      (Docker-gated; SKIP acceptable if Docker unavailable in CI).

---

# Related Specs

- `specs/contracts/http/internal/account-internal-provisioning.md` §Audit Action Codes
- `specs/contracts/http/internal/account-internal-provisioning.md` §Errors (BULK_LIMIT_EXCEEDED)
- `specs/services/account-service/architecture.md`
- `specs/services/account-service/data-model.md`

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md` (no content change needed
  — error codes are already correct in the spec; this fix aligns implementation with spec)

---

# Edge Cases

- **Exactly 1000 items**: must continue to return 200, not 400. Verify the boundary.
- **Empty items with `@Transactional` (no-readOnly)**: no audit row is written (existing
  behavior unchanged); confirm `historyRepository.save()` is still guarded by the
  `if (!items.isEmpty())` check.

---

# Failure Scenarios

- **`StatusChangeReason.OPERATOR_PROVISIONING_CREATE` addition breaks existing enum switch
  statements**: audit_heavy trait requires exhaustive handling; search all switch-on-enum
  sites and add the new case where needed.
- **BulkLimitExceededException thrown before validation**: if the controller size-checks
  before `@Valid` fires, missing other field validations (e.g. null email) on oversized
  requests will not be reported. This is acceptable — the limit guard is a fast reject,
  and the caller should fix the item count before resubmitting.
