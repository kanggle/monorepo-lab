# Task ID

TASK-BE-006-FIX

# Title

Fix missing event publishers found in TASK-BE-006 review

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-006

---

# Goal

Fix issues found during code review of TASK-BE-006.

Two security-critical events are registered in the outbox relay topic map but are never published: `auth.token.reuse.detected` (detected in `RefreshTokenUseCase` but event not published) and `session.revoked` (logout path completes but event not published). Additionally, all payloads are missing the `geoCountry` field required by the event contracts.

---

# Scope

## In Scope

1. Add `publishTokenReuseDetected(...)` method to `AuthEventPublisher` with all required fields from `specs/contracts/events/auth-events.md` (`accountId`, `reusedJti`, `originalRotationAt`, `reuseAttemptAt`, `ipMasked`, `deviceFingerprint`, `sessionsRevoked`, `revokedCount`).
2. Call `authEventPublisher.publishTokenReuseDetected(...)` in `RefreshTokenUseCase` at the reuse detection branch before throwing `SessionRevokedException`.
3. Add `publishSessionRevoked(...)` method to `AuthEventPublisher` with all required fields from `specs/contracts/events/session-events.md` (`accountId`, `revokedJtis`, `revokeReason`, `actorType`, `actorId`, `revokedAt`, `totalRevoked`).
4. Call `authEventPublisher.publishSessionRevoked(...)` in `LogoutUseCase` after token revocation.
5. Add `geoCountry` field to `SessionContext` (or resolve via a GeoResolver stub that returns `"XX"` when geo resolution is unavailable) and include it in all `AuthEventPublisher` payload maps where the contract requires it (`auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`).
6. Remove `KafkaProducerConfig.java` (or mark `@ConditionalOnMissingBean`) to avoid duplicate `KafkaTemplate` bean conflict with Spring Boot auto-configuration. Kafka producer settings are already fully declared in `application.yml`.

## Out of Scope

- Full geo-resolution integration (a stub returning `"XX"` is sufficient for now)
- Changes to other services' outbox relay
- TASK-BE-009 (full reuse detection enhancement)

---

# Acceptance Criteria

- [ ] `RefreshTokenUseCase` publishes `auth.token.reuse.detected` to the outbox on reuse detection before rethrowing
- [ ] `LogoutUseCase` publishes `session.revoked` to the outbox on successful logout revocation
- [ ] All `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded` payloads include a `geoCountry` field
- [ ] `auth.token.reuse.detected` payload contains all contract-required fields: `accountId`, `reusedJti`, `originalRotationAt`, `reuseAttemptAt`, `ipMasked`, `deviceFingerprint`, `sessionsRevoked`, `revokedCount`
- [ ] `session.revoked` payload contains all contract-required fields: `accountId`, `revokedJtis`, `revokeReason`, `actorType`, `actorId`, `revokedAt`, `totalRevoked`
- [ ] No duplicate `KafkaTemplate` bean (either remove `KafkaProducerConfig` or guard with `@ConditionalOnMissingBean`)
- [ ] All existing tests still pass

---

# Related Specs

- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/session-events.md`
- `platform/event-driven-policy.md`
- `specs/services/auth-service/dependencies.md`

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` — `auth.token.reuse.detected` payload spec
- `specs/contracts/events/session-events.md` — `session.revoked` payload spec

---

# Target Service

- `apps/auth-service`

---

# Implementation Notes

- `originalRotationAt`: when the reused token was rotated. This is the `issued_at` or `created_at` of the child token (the token generated when the original was rotated). Retrieve from `refreshTokenRepository` using `existsByRotatedFrom` → fetch the child token to get its `issuedAt`.
- `reuseAttemptAt`: `Instant.now()` at time of detection.
- `sessionsRevoked`: `true` (all tokens were revoked)
- `revokedCount`: result of `revokeAllByAccountId` (modify `RefreshTokenRepository.revokeAllByAccountId` to return a count, or call `countByAccountId` before revoking)
- `geoCountry`: add `String geoCountry` to `SessionContext` record. Default to `"XX"` when unknown. Request-layer code that constructs `SessionContext` should pass `"XX"` until geo resolution is implemented.
- For `LogoutUseCase.session.revoked`: `revokeReason = "USER_LOGOUT"`, `actorType = "user"`, `actorId = null`, `revokedJtis = [jti]`, `totalRevoked = 1`

---

# Edge Cases

- Reuse detection: child token may not be found in DB if rotation was partial. In that case, set `originalRotationAt` to `null` and proceed with publishing.
- `geoCountry` field: if geo resolver throws, fall back to `"XX"` — never fail event publishing.
- Logout with already-invalid token: if `findByJti` returns empty, do not publish `session.revoked` (nothing was revoked).

---

# Failure Scenarios

- If `publishTokenReuseDetected` fails to serialize, log error and continue — session revocation must not be blocked by event publishing failure (outbox write exception should be caught or the `@Transactional` boundary should be preserved correctly)

---

# Test Requirements

- Unit: `AuthEventPublisherTest` — verify `publishTokenReuseDetected` and `publishSessionRevoked` produce correct outbox entries with all required envelope fields
- Unit: `RefreshTokenUseCaseTest` — verify that on reuse detection, `authEventPublisher.publishTokenReuseDetected` is called before throw
- Unit: `LogoutUseCaseTest` — verify `authEventPublisher.publishSessionRevoked` is called when token is found and revoked
- Integration: extend `OutboxRelayIntegrationTest` to cover `auth.token.reuse.detected` relay

---

# Definition of Done

- [ ] `publishTokenReuseDetected` implemented and called in `RefreshTokenUseCase`
- [ ] `publishSessionRevoked` implemented and called in `LogoutUseCase`
- [ ] `geoCountry` field added to all relevant payloads
- [ ] No duplicate `KafkaTemplate` bean
- [ ] Tests passing
- [ ] Ready for review
