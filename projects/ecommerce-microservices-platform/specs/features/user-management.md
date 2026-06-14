# Feature: User Management

## Purpose

Manages user profile data and shipping addresses for authenticated users. Provides profile creation (triggered by signup event), profile query/update, address CRUD, and user withdrawal functionality.

## Related Services

| Service | Role |
|---|---|
| user-service | Primary owner — profile CRUD, address management, withdrawal, event publishing |
| ~~auth-service~~ | ~~Publishes UserSignedUp event to trigger initial profile creation; consumes UserWithdrawn event to invalidate authentication credentials~~ **REMOVED by TASK-BE-132 — IAM (iam-platform) is now the identity source. Profile creation triggers from IAM `AccountSignedUp` events; credential invalidation is IAM-internal.** |
| order-service | Consumes UserWithdrawn event to cancel active orders |
| web-store | Customer-facing profile view/edit, address management UI |
| platform-console | Admin user list and detail view |
| gateway-service | Request routing, user identity injection (X-User-Id header) |

## User Flows

### Profile Creation (Event-Driven)

1. IAM (iam-platform) publishes the account-signup event (IAM `AccountSignedUp`, consumed by ecommerce as `UserSignedUp`) upon successful registration in IAM
2. user-service consumes the event and creates initial profile (userId, email, name)
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

### User Withdrawal

1. User initiates account withdrawal
2. user-service transitions profile status to WITHDRAWN
3. user-service publishes UserWithdrawn event
4. order-service consumes event and cancels all active orders
5. Credential/session invalidation for the withdrawn user is handled IAM-internally (IAM consumes the withdrawal signal) — ecommerce no longer owns this step

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
- Profile email and name are sourced from IAM (iam-platform) via the account-signup event and are not directly modifiable
- Admin endpoints require admin role (enforced via gateway)
- Concurrent address modifications are handled with proper synchronization

## Related Contracts

- HTTP: `specs/contracts/http/user-api.md`
- Events: `specs/contracts/events/user-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| UserSignedUp | IAM (iam-platform) | user-service |
| UserProfileUpdated | user-service | platform-console (future), notification-service (future) |
| UserWithdrawn | user-service | order-service, IAM (credential/session invalidation, IAM-internal) |
