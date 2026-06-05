# Task ID

TASK-BE-270

# Title

Fix TASK-BE-258 — add salt to device_fingerprint masking SQL + add pii_masking_log to data-model spec

# Status

ready

# Owner

backend

# Task Tags

- code
- adr

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

Two issues found during review of TASK-BE-258:

1. **device_fingerprint salt missing (Warning)**: The task spec (Implementation Notes)
   and the Consumer Obligations table in `account-events.md` both specify
   `SHA256(account_id + 'salt')` for the `device_fingerprint` masking sentinel.
   The implementation uses `SHA2(:accountId, 256)` — i.e. SHA-256 of the bare
   account_id with **no salt**. Without a salt, the hash is deterministic and
   subject to pre-image attacks: anyone who knows a UUID account_id can verify
   whether a fingerprint matches that account, breaking the anonymization intent.
   The spec requires a fixed application-level salt to prevent this.

2. **`pii_masking_log` absent from data-model spec (Warning)**: V0009 adds the
   `pii_masking_log` table to the security-service schema, but
   `specs/services/security-service/data-model.md` does not exist.
   The architecture.md references a persistence section but the actual data model
   document is missing. Adding `pii_masking_log` to the spec is needed for:
   - On-call engineers understanding the table's purpose and retention policy.
   - Future schema migrations to know what columns the table owns.
   - Reviewer traceability for regulated/audit-heavy projects.

---

# Scope

## In Scope

- **Fix 1 — Salt in device_fingerprint hashing**:
  - Update `PiiMaskingLogJpaRepository.maskLoginHistory` SQL:
    change `SHA2(:accountId, 256)` to `SHA2(CONCAT(:accountId, :salt), 256)`.
  - Add a `@Value("${app.pii.masking.fingerprint-salt}")` parameter to
    `PiiMaskingLogJpaRepository` (or inject via `PiiMaskingService`).
  - Add the property to `application.yml` and `application-test.yml`.
  - Update Consumer Obligations table in `account-events.md` to document that
    the salt is an application-configured fixed value (not per-account random).
  - Update unit test `PiiMaskingServiceTest` to verify the salted fingerprint.
  - Update integration test `PiiMaskingIntegrationTest` assertion
    (`device_fingerprint` length check still valid; add salt-specific note in comment).

- **Fix 2 — Create `specs/services/security-service/data-model.md`**:
  - Document the full schema: `login_history`, `suspicious_events`,
    `account_lock_history`, `processed_events`, `outbox_events`, `pii_masking_log`.
  - For each table: purpose, key columns (with data classification R1), append-only
    vs mutable, retention policy stub.
  - Note: `login_history` and `suspicious_events` are the primary PII-carrying
    tables; `pii_masking_log` is internal idempotency/audit.

## Out of Scope

- No schema migration needed — column type/length is unchanged.
- No changes to `security-events.md` or other event contracts.
- No changes to other services.

---

# Acceptance Criteria

- [ ] `PiiMaskingLogJpaRepository.maskLoginHistory` uses `SHA2(CONCAT(:accountId, :salt), 256)`.
- [ ] Salt is injected via `@Value("${app.pii.masking.fingerprint-salt}")` and present in `application.yml`.
- [ ] `account-events.md` Consumer Obligations table updated: `device_fingerprint=SHA256(accountId||salt)` note clarified as app-level fixed salt.
- [ ] `PiiMaskingServiceTest` verifies the SQL call receives the correct salt argument.
- [ ] `specs/services/security-service/data-model.md` created and covers all 6 tables including `pii_masking_log`.
- [ ] `./gradlew :projects:global-account-platform:apps:security-service:check` PASS.

---

# Related Specs

- `specs/contracts/events/account-events.md` § Consumer Obligations (TASK-BE-258)
- `specs/services/security-service/architecture.md`
- `specs/services/security-service/data-model.md` (to be created)
- `rules/traits/regulated.md` R7 (PII anonymization — irreversibility requirement)
- `rules/traits/regulated.md` R2 (salted hash requirement for stored sensitive data)

---

# Related Contracts

- `specs/contracts/events/account-events.md` (Consumer Obligations table wording update)

---

# Edge Cases

- **Salt rotation**: The fixed application salt is intentionally static (same as bcrypt salt for email hash). Salt rotation would re-hash all existing masked fingerprints — out of scope. Document this limitation in `data-model.md`.
- **Existing masked rows (if any)**: Migration is schema-only; rows masked before this fix will have un-salted SHA-256 hashes. A data repair migration is out of scope — document the known gap in `data-model.md`.

---

# Failure Scenarios

- **Missing salt property on startup**: Spring will fail to bind `@Value` → application startup failure. This is the desired fail-fast behavior.
- **Empty salt string**: `SHA2(CONCAT(:accountId, ''), 256)` degrades to unsalted hash. Add `@NotBlank` validation on the property.

