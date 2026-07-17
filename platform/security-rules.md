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

## A verified token proves authentication, not authorization

Because each project declares exactly **one** issuer (§ Authentication), that issuer mints **both** end-user
credentials and machine (service-workload) credentials. They therefore carry the **same issuer and the same
signature**, and differ only in their *claims*.

It follows that a surface intended only for service-to-service traffic **MUST NOT** be gated on
`issuer + signature + "is authenticated"` alone, nor on a bare token-decode. **An end-user token satisfies
all of those.** A service that gates an internal-only surface this way is not protecting it: any holder of
any valid user token of that project can call it.

Every internal-only surface MUST additionally require a claim that **only a machine credential can carry**.
Exactly one of:

- **Subject allow-list** — check the client-credentials subject (the client id) against an explicit
  allow-list. Appropriate when the set of legitimate callers is small and static.
- **Required scope** — require a scope that is granted only to workload clients and never to any end-user
  grant. Appropriate when callers are many or expected to change; it is self-maintaining, since a new
  workload client is provisioned with the scope.

Two constraints on how the discriminator is applied:

- **Enforce it where the token is validated** (the decoder/validator), not only in a filter. A check that a
  test profile or an alternate filter chain can bypass is not enforcement — and a test that bypasses it
  passes without ever exercising the rule.
- **Before enforcing, prove that every legitimate caller already carries the discriminator.** Enumerate the
  callers and verify each one; do not infer it from configuration. Enforcing without that inventory converts
  an authorization gap into an outage.

> **Why this is written down.** This is not a hypothetical. Fleet services re-created this exact defect on
> four separate occasions, each time by mirroring a sibling that had it. The shared *code* for the check was
> eventually promoted to a shared library — but the **rule** was not, so every new internal surface had to
> re-derive it, and the ones that didn't shipped the gap behind green tests. If a precedent you are mirroring
> is itself defective, the mirror inherits the defect: read the validator the precedent actually installs
> rather than trusting that it must be correct because it exists.

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

# Related Authorization Contracts

Authorization beyond authentication is governed by two **axis-②** contracts (read when a task narrows operator access by data slice or runtime condition):

- [`abac-data-scope.md`](abac-data-scope.md) — 1단계: attribute-based **data-scope** (over *which slice* of a tenant's data an operator may act; ADR-MONO-025).
- [`access-conditions.md`](access-conditions.md) — 2단계: closed-enum, restriction-only **access-condition** gates (*under which circumstances* a permission is live; ADR-MONO-026).

These complement the RBAC axes (ADR-019/020/021/024). They are also listed in [`README.md`](README.md) § What Lives Here.

---

# Change Rule

Any deviation from these rules requires an explicit ADR (Architecture Decision Record)
documented under `docs/adr/` (monorepo-wide) or `projects/<project>/docs/adr/` (project-scoped) before implementation.