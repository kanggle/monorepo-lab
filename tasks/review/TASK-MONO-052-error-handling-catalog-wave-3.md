# Task ID

TASK-MONO-052

# Title

`platform/error-handling.md` catalog audit — ecommerce / scm / saas / fan-platform drift backfill (B common-rule refactor wave 3)

# Status

ready

# Owner

backend / monorepo

# Task Tags

- spec
- catalog
- rules

---

# Goal

Close the **catalog ↔ implementation drift** in the platform-wide error registry (`platform/error-handling.md`) for the **remaining 4 domains** — ecommerce / scm / saas (GAP) / fan-platform. Wave 1 (PR #328) seeded `[domain: scm]`. Wave 2 (PR #352, TASK-MONO-051) closed the wms drift. This Wave 3 covers everything else.

An audit across 20+ services (ecommerce 10 + scm 2 + GAP 6 + fan-platform 2) surfaces **50+ error codes emitted in production code that are absent from the catalog**, plus a handful of stale catalog entries (no longer emitted by code), plus 8 spec-author decision points (HTTP mismatches / cross-domain duplicates / Platform-Common promotion candidates).

This task does **not** punish the gap — it backfills the catalog, records the decisions, and (per `platform/error-handling.md` § Change Rule "New error codes must be added to this document before being used in implementation") restores the rule's integrity.

This is **B (Common Rule Refactoring) wave 3** per memory `project_b_common_rule_refactor_pending.md` candidate #2. D4 OVERRIDE applies (sole user-acknowledged risk path — see [ADR-MONO-003](../../docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md) § 3.4 risk 2).

---

# Scope

## In Scope

Backfill the catalog for the 4 domains. Concrete drift tables below.

### A. ecommerce drift (Type A — missing from catalog)

| Code | HTTP | Source exception class (service) | Suggested catalog section |
|---|---|---|---|
| `MEDIA_NOT_FOUND` | 404 | `MediaNotFoundException` (product-service) | Product |
| `MEDIA_VALIDATION_FAILED` | 400 | `MediaValidationException` (product-service) | Product |
| `STORAGE_UNAVAILABLE` | 503 | `StorageUnavailableException` (product-service) | Product |
| `SEARCH_UNAVAILABLE` | 503 | `SearchException` (search-service) | Search |
| `EMAIL_ALREADY_EXISTS` | 409 | `EmailAlreadyExistsException` (auth-service) | **NEW** `## Auth  [domain: ecommerce]` |
| `INVALID_CREDENTIALS` | 401 | `InvalidCredentialsException` (auth-service) | **NEW** Auth |
| `INVALID_REFRESH_TOKEN` | 401 | `InvalidRefreshTokenException` (auth-service) | **NEW** Auth |
| `REFRESH_TOKEN_REVOKED` | 401 | `RefreshTokenRevokedException` (auth-service) | **NEW** Auth |
| `OAUTH_UPSTREAM_ERROR` | 502 | `OAuthUpstreamException` (auth-service) | **NEW** Auth |
| `INVALID_STATE` | 400 | `OAuthException` (auth-service) | **Platform-Common Auth promotion** (also emitted by GAP auth-service — D4 decision) |
| `DATA_INTEGRITY_VIOLATION` | 409 | user-service `GlobalExceptionHandler` catch-all | **Platform-Common General** |

**Type A net**: 11 codes (1 promoted to Platform-Common Auth, 1 promoted to Platform-Common General, 9 domain-specific).

### B. scm drift (Type A)

| Code | HTTP | Source exception class | Suggested catalog section |
|---|---|---|---|
| `PO_ALREADY_CONFIRMED` | 422 | `PoAlreadyConfirmedException` (procurement-service) | Procurement |
| `PO_QUANTITY_EXCEEDED` | 422 | `PoQuantityExceededException` (procurement-service) | Procurement |
| `CATALOG_SKU_UNKNOWN` | 422 | `CatalogSkuUnknownException` (procurement-service) | Procurement |
| `IDEMPOTENCY_KEY_MISMATCH` | 422 | `IdempotencyKeyMismatchException` (procurement-service) | Procurement |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | `MissingRequestHeaderException` handler guard (procurement-service) | **Platform-Common Transactional Trait promotion** (D5 decision — applies to any T1 idempotency service) |
| `SUPPLIER_UNAVAILABLE` | 503 | `SupplierUnavailableException` (procurement-service) | Procurement |
| `NODE_UNREACHABLE` | 503 | `NodeUnreachableException` (inventory-visibility-service) | Inventory Visibility |
| `SNAPSHOT_STALE` | 200 | `SnapshotStaleException` (inventory-visibility-service) | Inventory Visibility (**rename** existing stale `STALENESS_THRESHOLD_EXCEEDED` — D6 decision) |

**Type A net**: 8 codes.

### C. saas (GAP) drift (Type A — highest volume)

#### account-service additions

| Code | HTTP | Source exception | Section |
|---|---|---|---|
| `EMAIL_ALREADY_VERIFIED` | 409 | `EmailAlreadyVerifiedException` | Account |
| `RATE_LIMITED` | 429 | `RateLimitedException` | Account |
| `AUTH_SERVICE_UNAVAILABLE` | 503 | `AuthServicePort.AuthServiceUnavailable` | Account (already in catalog — verify) |
| `BULK_LIMIT_EXCEEDED` | 400 | `BulkLimitExceededException` | Account |

#### auth-service additions

| Code | HTTP | Source exception | Section |
|---|---|---|---|
| `CREDENTIALS_INVALID` | 401 | `CredentialsInvalidException` | Auth/Token (D3 decision — GAP-specific alias of ecommerce `INVALID_CREDENTIALS`) |
| `PASSWORD_RESET_TOKEN_INVALID` | 400 | `PasswordResetTokenInvalidException` | Auth/Token |
| `CREDENTIAL_ALREADY_EXISTS` | 409 | `CredentialAlreadyExistsException` | Auth/Token |
| `SESSION_REVOKED` | 401 | `SessionRevokedException` | Auth/Token |
| `SESSION_NOT_FOUND` | 404 | `SessionNotFoundException` | Auth/Token |
| `SESSION_OWNERSHIP_MISMATCH` | 403 | `SessionOwnershipMismatchException` | Auth/Token |
| `UNSUPPORTED_PROVIDER` | 400 | `UnsupportedProviderException` | Auth/Token |
| `INVALID_REDIRECT_URI` | 400 | `InvalidOAuthRedirectUriException` | Auth/Token |
| `EMAIL_REQUIRED` | 422 | `OAuthEmailRequiredException` | Auth/Token |
| `PROVIDER_ERROR` | 502 | `OAuthProviderException` | Auth/Token |
| `PASSWORD_POLICY_VIOLATION` | 400 | `PasswordPolicyViolationException` | Auth/Token |

#### admin-service additions (operator portal — new section)

| Code | HTTP | Source exception | Section |
|---|---|---|---|
| `REASON_REQUIRED` | 400 | `ReasonRequiredException` | **NEW** `## Admin  [domain: saas]` |
| `PERMISSION_DENIED` | 403 | `PermissionDeniedException` | **NEW** Admin (cross-service: also community + membership) |
| `INVALID_BOOTSTRAP_TOKEN` | 401 | `InvalidBootstrapTokenException` | **NEW** Admin |
| `INVALID_2FA_CODE` | 401 | `InvalidTwoFaCodeException` | **NEW** Admin |
| `TOTP_NOT_ENROLLED` | 404 | `TotpNotEnrolledException` | **NEW** Admin |
| `REFRESH_TOKEN_REUSE_DETECTED` | 401 | `RefreshTokenReuseDetectedException` | **NEW** Admin |
| `INVALID_RECOVERY_CODE` | 401 | `InvalidRecoveryCodeException` | **NEW** Admin |
| `ENROLLMENT_REQUIRED` | 401 | `EnrollmentRequiredException` | **NEW** Admin |
| `AUDIT_FAILURE` | 500 | `AuditFailureException` | **NEW** Admin |
| `BATCH_SIZE_EXCEEDED` | 422 | `BatchSizeExceededException` | **NEW** Admin |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | `IdempotencyKeyConflictException` | **NEW** Admin |
| `OPERATOR_EMAIL_CONFLICT` | 409 | `OperatorEmailConflictException` | **NEW** Admin |
| `OPERATOR_NOT_FOUND` | 404 | `OperatorNotFoundException` | **NEW** Admin |
| `SELF_SUSPEND_FORBIDDEN` | 400 | `SelfSuspendForbiddenException` | **NEW** Admin |
| `TENANT_ID_RESERVED` | 400 | `TenantIdReservedException` | **NEW** Admin / Tenant |
| `CURRENT_PASSWORD_MISMATCH` | 400 | `CurrentPasswordMismatchException` | **NEW** Admin |

#### community + membership additions (new sections)

| Code | HTTP | Source exception | Section |
|---|---|---|---|
| `MEMBERSHIP_REQUIRED` | 403 | `MembershipRequiredException` (GAP community-service) | **NEW** `## Community  [domain: saas]` (distinct from fan-platform community) |
| `ALREADY_FOLLOWING` | 409 | `AlreadyFollowingException` (GAP community-service) | **NEW** Community |
| `NOT_FOLLOWING` | 404 | `NotFollowingException` (GAP community-service) | **NEW** Community |
| `POST_STATUS_TRANSITION_INVALID` | 422 | illegal state guard (GAP community-service) | **NEW** Community |
| `SUBSCRIPTION_ALREADY_ACTIVE` | 409 | `SubscriptionAlreadyActiveException` | **NEW** `## Membership  [domain: saas]` |
| `ACCOUNT_NOT_ELIGIBLE` | 409 | `AccountNotEligibleException` | **NEW** Membership |
| `ACCOUNT_STATUS_UNAVAILABLE` | 503 | `AccountStatusUnavailableException` | **NEW** Membership |
| `SUBSCRIPTION_NOT_FOUND` | 404 | `SubscriptionNotFoundException` | **NEW** Membership |
| `SUBSCRIPTION_NOT_ACTIVE` | 409 | `SubscriptionNotActiveException` | **NEW** Membership |
| `PLAN_NOT_FOUND` | 404 | `PlanNotFoundException` | **NEW** Membership |

**Type A saas net**: ~32 codes (3 new sub-sections: Admin, Community [saas], Membership).

### D. fan-platform drift (Type A)

| Code | HTTP | Source exception | Section |
|---|---|---|---|
| `ARTIST_GROUP_NOT_FOUND` | 404 | `ArtistGroupNotFoundException` | Artist |
| `FANDOM_NOT_FOUND` | 404 | `FandomNotFoundException` | Artist |
| `STAGE_NAME_CONFLICT` | 409 | `StageNameConflictException` | Artist |
| `GROUP_NAME_CONFLICT` | 409 | `GroupNameConflictException` | Artist |
| `ALREADY_MEMBER` | 422 | `AlreadyMemberException` | Artist |
| `FANDOM_ALREADY_EXISTS` | 422 | `FandomAlreadyExistsException` | Artist |
| `ARTIST_NOT_PUBLISHED` | 422 | `ArtistNotPublishedException` | Artist |
| `ARTIST_ARCHIVED` | 422 | `ArtistArchivedException` | Artist |
| `COMMENT_NOT_FOUND` | 404 | `CommentNotFoundException` | Community (replace existing — already in catalog but verify) |
| `SELF_FOLLOW_FORBIDDEN` | 422 | `SelfFollowForbiddenException` | Community |
| `POST_STATUS_TRANSITION_INVALID` | 422 | `InvalidStateTransitionException` | Community |
| `EDIT_WINDOW_EXPIRED` | 422 | `EditWindowExpiredException` | Community |
| `NOT_FOLLOWING` | 404 | `NotFollowingException` | Community |
| `ALREADY_FOLLOWING` | 409 | `AlreadyFollowingException` | Community |
| `MEMBERSHIP_REQUIRED` | 403 | `MembershipRequiredException` | Community |

**Type A fan-platform net**: 15 codes.

---

## Stale catalog entries (Type B — remove or rename)

| Catalog code | Reason | Action |
|---|---|---|
| `USER_ALREADY_WITHDRAWN` (ecommerce User) | No exception class / handler emits this; withdraw validation uses generic codes | **Mark as v2-planned** (note in catalog) or remove |
| `INVALID_WISHLIST_REQUEST` (ecommerce Wishlist) | Wishlist validation handled via generic `VALIDATION_ERROR` — no dedicated exception | **Mark as v2-planned** |
| `STALENESS_THRESHOLD_EXCEEDED` (scm Inventory Visibility) | Code emits `SNAPSHOT_STALE` instead — naming mismatch | **Rename to `SNAPSHOT_STALE`** (D6 decision) |
| `SETTLEMENT_PERIOD_LOCKED` (scm Procurement) | No `SettlementPeriodLockedException` — deferred to v2 settlement-service | **Mark as v2-planned** |
| `RECONCILIATION_DISCREPANCY_OPEN` (scm Procurement) | Same — deferred to v2 settlement-service | **Mark as v2-planned** |
| `TOKEN_REUSE` (saas Auth/Token) | Only `TOKEN_REUSE_DETECTED` is emitted; `TOKEN_REUSE` is a stale duplicate | **Remove** (or document as legacy alias) |
| `FOLLOW_LIMIT_EXCEEDED` (fan-platform Artist) | No exception class | **Mark as v2-planned** |
| `FANDOM_METADATA_INVALID` (fan-platform Artist) | Generic `VALIDATION_ERROR` handles this | **Mark as v2-planned** |
| `REACTION_INVALID_TYPE` (fan-platform Community) | Generic `VALIDATION_ERROR` handles | **Mark as v2-planned** |
| `FEED_QUERY_INVALID` (fan-platform Community) | Generic `VALIDATION_ERROR` handles | **Mark as v2-planned** |

---

## Decision points + recorded decisions

8 spec-author judgment calls. Decisions recorded here become catalog state on impl.

### D1. `ACCOUNT_LOCKED` / `ACCOUNT_DORMANT` HTTP mismatch

Catalog says 423 (WebDAV `Locked`, RFC 4918). GAP `auth-service` handlers return 403. **Decision**: keep catalog at **423** (the more semantically correct status — locked != forbidden). Add a follow-up `TODO` note in catalog flagging the code-side mismatch; do NOT change code in this PR. A separate fix task can correct the handler status.

### D2. `TOKEN_REUSE` vs `TOKEN_REUSE_DETECTED`

Both currently in catalog. Only `TOKEN_REUSE_DETECTED` is emitted. **Decision**: remove `TOKEN_REUSE` from catalog (clean redundancy). Keep `TOKEN_REUSE_DETECTED` as canonical.

### D3. `CREDENTIALS_INVALID` (GAP) vs `INVALID_CREDENTIALS` (ecommerce)

Two services in two different domains, same semantic, different strings. **Decision**: keep both. Document each in its own domain section with a cross-domain note explaining the naming divergence. Future standardization (e.g. promote `INVALID_CREDENTIALS` to Platform-Common and deprecate `CREDENTIALS_INVALID`) is a separate task.

### D4. `INVALID_STATE` (OAuth state CSRF check)

Emitted by both ecommerce `auth-service` (via `OAuthException`) and GAP `auth-service` (via `InvalidOAuthStateException`), both 400. **Decision**: promote to **Platform-Common Authentication** section as a shared code (OAuth state validation is an RFC-level semantic, not domain-specific).

### D5. `IDEMPOTENCY_KEY_REQUIRED` (handler-emitted)

scm procurement-service emits this via `MissingRequestHeaderException` guard (not a domain exception class). Per Transactional Trait T1, any idempotency-enforcing service needs this code. **Decision**: promote to **Platform-Common Transactional Trait** section.

### D6. `STALENESS_THRESHOLD_EXCEEDED` (catalog) vs `SNAPSHOT_STALE` (code)

Same concept, different names. Code is the shipping reality. **Decision**: rename catalog entry to `SNAPSHOT_STALE` (preserve HTTP 200 + meta.staleness semantic).

### D7. ecommerce `auth-service` section missing

8 domain-specific codes (email/credentials/OAuth) with no catalog section. **Decision**: create **new `## Auth  [domain: ecommerce]`** section (sibling to existing Product / Order / Payment / etc).

### D8. Cross-project community code reuse (fan-platform + GAP)

`ALREADY_FOLLOWING` / `NOT_FOLLOWING` / `MEMBERSHIP_REQUIRED` / `POST_STATUS_TRANSITION_INVALID` emitted by both fan-platform `community-service` and GAP `community-service` (distinct services in different projects). **Decision**: list entries in **both** domain sections (each domain owns its own community service) + add a 1-line cross-project note. Same string, same HTTP — semantic alignment intentional.

---

## Out of Scope (deferred — possible TASK-MONO-052-followups)

- **Code-side HTTP status corrections** (`ACCOUNT_LOCKED` 403→423 in GAP auth-service handler). Spec-only task.
- **Replacing generic `VALIDATION_ERROR` with dedicated exception classes** for stale catalog entries (`INVALID_WISHLIST_REQUEST` etc). If desired, file dedicated impl tasks per service.
- **Strengthening the "new error codes must be added before being used" rule** with a CI grep gate. Out of scope; consider for a future TASK-MONO-053 follow-up.

---

# Acceptance Criteria

- [ ] ~66 missing codes added to `platform/error-handling.md` per the 4 domain tables (11 ecommerce + 8 scm + 32 saas + 15 fan-platform). Each entry: `Code | HTTP | Description`.
- [ ] 3 new section headers added: `## Auth  [domain: ecommerce]` + `## Admin  [domain: saas]` + `## Community  [domain: saas]` + `## Membership  [domain: saas]`.
- [ ] 10 stale catalog entries marked as v2-planned, removed, or renamed per Type B table.
- [ ] 8 decision points (D1–D8) recorded in this task spec **and** applied to catalog.
- [ ] `INVALID_STATE` promoted to Platform-Common Authentication (D4).
- [ ] `IDEMPOTENCY_KEY_REQUIRED` promoted to Platform-Common Transactional Trait (D5).
- [ ] `STALENESS_THRESHOLD_EXCEEDED` renamed to `SNAPSHOT_STALE` (D6).
- [ ] `TOKEN_REUSE` removed (D2).
- [ ] Cross-project community code reuse documented (D8).
- [ ] No code changes to services — spec-only update.

---

# Related Specs

- `platform/error-handling.md` (target)
- `rules/domains/ecommerce.md` § Standard Error Codes (cross-ref)
- `rules/domains/scm.md` § Standard Error Codes (cross-ref)
- `rules/domains/saas.md` § Standard Error Codes (cross-ref)
- `rules/domains/fan-platform.md` § Standard Error Codes (cross-ref)
- `rules/traits/transactional.md` § T1 (D5 promotion rationale)
- `ADR-MONO-003-phase-5-template-extraction-deferred.md` § 3.4 risk 2 (D4 OVERRIDE rationale)
- `tasks/done/TASK-MONO-051-error-handling-catalog-audit.md` (Wave 2 pattern reference)

# Related Skills

- `.claude/skills/refactor-spec` (skill applies to spec drift cleanup)

---

# Related Contracts

None — registry is a catalog spec, not a contract. The codes themselves are already-shipping contracts (services emit them in 4xx/5xx responses); this task records them in the canonical registry.

---

# Target Service

- Spec: `platform/error-handling.md` (shared)
- Audit source: `projects/{ecommerce-microservices-platform,scm-platform,global-account-platform,fan-platform}/apps/*/src/main/java/**/*Exception.java`

---

# Architecture

N/A — spec-only registry update. No service architecture impact.

---

# Implementation Notes

- Use existing catalog section structure. New sections follow the same template (heading + 1-line owner attribution + `Code | HTTP | Description` table).
- For codes that already exist in code, copy the description from the exception class javadoc or constructor message. If javadoc is missing, write a 1-line description matching the handler's intent.
- The audit was performed by backend-engineer agent (Sonnet) in chat. Future re-audit command:
  ```
  grep -rn 'return\s\+"[A-Z][A-Z_]\+"' projects/*/apps/*/src/main/java/**/*Exception.java
  ```
  Plus `@ExceptionHandler` mappings inside each service's `GlobalExceptionHandler` (or `AuthExceptionHandler` / `AdminExceptionHandler` / `QueryExceptionHandler`).

---

# Edge Cases

- **HTTP status mismatch** (D1 — `ACCOUNT_LOCKED` 423 vs 403): record catalog at 423 + flag with a TODO note; do NOT touch service code in this PR.
- **Cross-project same-string codes** (D8): not collisions — distinct services in distinct projects, same semantic. Keep both entries.
- **Codes emitted by handler guards rather than domain exceptions** (D5 — `IDEMPOTENCY_KEY_REQUIRED`): valid catalog entries; the exception class need not exist in domain code.
- **Codes in catalog but absent from code** (Type B): not all are stale — some are v2-planned. Mark with a `(v2-planned)` suffix in the description rather than deleting outright.

---

# Failure Scenarios

- **Catalog entry contradicts code behavior**: e.g. catalog says 422 but code returns 409. Mitigation: cross-check against the service's `GlobalExceptionHandler` mapping during impl. Prefer the **code's behavior** (real shipping contract) unless the code clearly violates documented semantic (in which case flag with TODO).
- **Same code, different descriptions in different services**: prefer the most inclusive description and note multi-service emission in catalog.
- **agentId references stale code**: the audit was done at commit `5e8867dc` (post-PR #370/371). If code drift occurs before merge, re-run the grep audit on impl day.

---

# Test Requirements

N/A — spec-only update. Verification = manual review:
- `grep -n '| \?\`[A-Z_]\+\`' platform/error-handling.md` includes all 66 new codes.
- Visual inspection: 4 new sections (ecommerce Auth, saas Admin, saas Community, saas Membership) have correct heading depth (`## ...`) + domain tag.
- No duplicate rows (same code in same section).

---

# Definition of Done

- [ ] All 66 codes added per the 4 domain tables.
- [ ] 4 new section headers added.
- [ ] 10 stale entries handled (rename / remove / mark v2-planned).
- [ ] 8 decisions (D1–D8) reflected in catalog.
- [ ] Impl PR description references this task + the grep audit command for future replays.
- [ ] Wave 3 closure recorded in memory `project_b_common_rule_refactor_pending.md` (candidate #2 status → Wave 3 DONE).

---

# Provenance

Filed by memory `project_b_common_rule_refactor_pending.md` candidate #2 (`platform/error-handling.md` catalog audit). Wave 1 was PR #328 (which added `[domain: scm]` section + 11 fixes). Wave 2 was TASK-MONO-051 / PR #352 (wms backfill). Wave 3 is this task (ecommerce + scm + GAP + fan-platform).

D4 OVERRIDE applies — user-acknowledged risk path per [ADR-MONO-003](../../docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md) § 3.4 risk 2. last_churn marker resets on impl PR merge.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical catalog append — 66 entries + 4 new sections, decisions enumerated above).
