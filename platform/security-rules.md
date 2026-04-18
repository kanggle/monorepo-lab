# Security Rules

Defines platform-wide security requirements that all services must follow.

---

# Authentication

- JWT (JSON Web Token) is the standard authentication mechanism.
- Each project declares exactly one service (in `PROJECT.md`) as the **sole issuer** of access tokens and refresh tokens. If the project delegates to an external identity provider, the project still declares which internal surface trusts which issuer.
- The gateway service (as declared in `PROJECT.md`) verifies JWT on all inbound requests before routing.
- Services behind the gateway may trust the identity forwarded by the gateway via identity headers (`X-User-Id`, `X-User-Role`).
- Services MUST NOT re-implement token issuance logic. Only the declared issuer mints tokens.

---

# Authorization

- Each service is responsible for its own authorization logic.
- Authorization must be enforced at the application layer, not only at the gateway.
- Role and permission checks must be applied before executing business logic.
- Services must not rely solely on the gateway for authorization decisions.

---

# Transport Security

- All external-facing endpoints must use HTTPS only.
- HTTP requests from external clients must be rejected or redirected to HTTPS.
- Internal service-to-service communication must use a secure channel (TLS or internal trusted network).

---

# Sensitive Data

- Credentials, tokens, and secrets must not be logged.
- Personally identifiable information (PII) must not appear in logs or error messages.
- If the project handles payment or similarly sensitive credentials (card numbers, CVV, medical records, government ids), exactly one service MUST own that data (declared in `PROJECT.md` and in its `specs/services/<service>/architecture.md`).
- No other service may store or transit raw payment credentials or equivalent sensitive material — only references (e.g., payment intent id, vaulted token) may cross service boundaries.
- Secrets must be managed through environment variables or a secrets manager. Hard-coded secrets are forbidden.

---

# Input Validation

- All external inputs must be validated at the service boundary before processing.
- Validation must occur in the presentation or application layer, not only in the domain.
- Services must reject malformed or unexpected inputs with an appropriate error response.

---

# API Security

- Public APIs must not expose internal identifiers, stack traces, or implementation details in error responses.
- Rate limiting is applied at the gateway level.
- Services must not trust user-supplied identity claims that bypass the gateway.

---

# Dependency Security

- Third-party dependencies must be kept up to date.
- Known vulnerable dependencies must be resolved before deployment.
- Dependency scanning must be part of the CI pipeline.

---

# Change Rule

Any deviation from these rules requires an explicit ADR (Architecture Decision Record)
documented under `knowledge/adr/` before implementation.