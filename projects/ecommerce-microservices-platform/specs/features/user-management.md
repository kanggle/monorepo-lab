# Feature: User Management

## Purpose

Manages user profile data and shipping addresses for authenticated users. Provides profile creation (triggered by the IAM `account.created` lifecycle event), profile query/update, address CRUD, and event-driven withdrawal/anonymization (reacting to IAM `account.deleted`) — [ADR-MONO-037](../../../../docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md).

## Related Services

| Service | Role |
|---|---|
| user-service | Primary owner — profile CRUD, address management, withdrawal, event publishing |
| ~~auth-service~~ | ~~Publishes UserSignedUp event to trigger initial profile creation; consumes UserWithdrawn event to invalidate authentication credentials~~ **REMOVED by TASK-BE-132 — IAM (iam-platform) is now the identity source. Profile creation triggers from IAM `account.created` events (ADR-MONO-037); credential invalidation is IAM-internal.** |
| order-service | Consumes UserWithdrawn event to cancel active orders |
| web-store | Customer-facing profile view/edit, address management UI |
| platform-console | Admin user list and detail view |
| gateway-service | Request routing, user identity injection (X-User-Id header) |

## User Flows

### Profile Creation (Event-Driven)

1. IAM (iam-platform) publishes `account.created` upon successful registration in IAM (ADR-MONO-037)
2. user-service consumes it and creates a **minimal** profile (userId = `accountId`); `email`/`name` are NOT in the event (emailHash-only) — they are populated later from the OIDC token / profile-update
3. Profile is created in ACTIVE status

### Profile Query

1. Authenticated user sends GET /api/users/me
2. user-service returns profile (userId, email, name, nickname, phone, profileImageUrl, status)

### Profile Update

1. Authenticated user sends PATCH /api/users/me with partial update (nickname, phone, profileImageUrl)
2. user-service validates and applies changes
3. user-service publishes UserProfileUpdated event

### Address Management

1. GET /api/users/me/addresses — list all shipping addresses
2. POST /api/users/me/addresses — add new address (label, recipient, phone, zipCode, address1, address2, isDefault)
3. PATCH /api/users/me/addresses/{addressId} — update existing address
4. DELETE /api/users/me/addresses/{addressId} — delete address

### User Withdrawal & Anonymization (Event-Driven, two-phase)

IAM owns the deletion lifecycle; ecommerce *reacts* to IAM `account.deleted` (the self-service HTTP withdrawal endpoint was removed by TASK-BE-387). The `user-service` consumer branches on the event's own `anonymized` flag — it does not self-schedule on `gracePeriodEndsAt` (ADR-MONO-037 P2/P3):

1. **Phase 1 — grace entry (`anonymized=false`)**: user-service resolves the profile by `accountId` → transitions status to WITHDRAWN → publishes UserWithdrawn.
2. order-service consumes UserWithdrawn and cancels all active orders.
3. **Phase 2 — post-grace (`anonymized=true`)**: user-service anonymizes profile PII (`email`/`name`/`nickname`/`phone`/`profileImageUrl` cleared), preserving `userId` for FK/audit/order integrity — the TASK-BE-258 GDPR obligation.
4. Both phases are idempotent + fail-soft over the monotonic ACTIVE → WITHDRAWN → anonymized transition.
5. Credential/session invalidation for the withdrawn account is handled IAM-internally — ecommerce no longer owns this step.

> **Scope boundary (ADR-MONO-037 P3):** v1 anonymizes user-service profile PII only. The order-service-held PII cascade (shipping addresses, recipient names on historical orders) is a documented, tracked deferred follow-up — not a silent omission.

### Admin User Management

1. Admin sends GET /api/admin/users with optional filters (status, email, pagination)
2. user-service returns paginated user list
3. Admin sends GET /api/admin/users/{userId} for detailed profile view

## Business Rules

- User statuses: ACTIVE, SUSPENDED, WITHDRAWN
- Maximum 10 addresses per user (ADDRESS_LIMIT_EXCEEDED on exceeding)
- One address can be designated as default (isDefault)
- Default address cannot be deleted while other addresses exist (DEFAULT_ADDRESS_CANNOT_BE_DELETED)
- User ID is sourced from X-User-Id header injected by gateway
- user-service must not expose or modify authentication credentials
- Profile `email`/`name` are sourced from the IAM-issued OIDC id_token (`profile`/`email` scopes), not from the lifecycle event — `account.created` is emailHash-only (no raw PII); they are not directly modifiable via the profile-update API (ADR-MONO-037 P1)
- Admin endpoints require admin role (enforced via gateway)
- Concurrent address modifications are handled with proper synchronization

## Related Contracts

- HTTP: `specs/contracts/http/user-api.md`
- Events: `specs/contracts/events/user-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| `account.created` | IAM (iam-platform) | user-service, notification-service |
| `account.deleted` | IAM (iam-platform) | user-service (two-phase withdraw → anonymize) |
| UserProfileUpdated | user-service | platform-console (future), notification-service (future) |
| UserWithdrawn | user-service | order-service, IAM (credential/session invalidation, IAM-internal) |
