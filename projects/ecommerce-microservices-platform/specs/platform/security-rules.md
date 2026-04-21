# Security Rules

Defines platform-wide security requirements that all services must follow.

---

# Authentication

- JWT (JSON Web Token) is the standard authentication mechanism.
- `auth-service` is the sole issuer of access tokens and refresh tokens.
- Token validation at the gateway: `gateway-service` verifies JWT on all inbound requests before routing.
- Services behind the gateway may trust the identity forwarded by the gateway.
- Services must not re-implement token issuance logic.

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
- Payment-related sensitive data (card numbers, CVV) is owned exclusively by `payment-service`.
- No other service may store or transit raw payment credentials.
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