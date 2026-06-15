> **DEPRECATED — 2026-05-04 (TASK-BE-132). Historical reference only; see [README.md](README.md).**

# auth-service Observability

Defines business metrics specific to auth-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `auth_signup_total` | Counter | Total successful signups |
| `auth_login_total` | Counter | Total login attempts (success/failure) |
| `auth_login_failure_total` | Counter | Total failed login attempts by reason (invalid_credentials, rate_limited, account_deactivated) |
| `auth_logout_total` | Counter | Total logout requests |
| `auth_token_refresh_total` | Counter | Total token refresh attempts (success/failure) |
| `auth_session_eviction_total` | Counter | Total sessions evicted due to concurrent session limit |
| `event_publish_failure_total` | Counter | Total failed event publish attempts (tags: service, event_type) |

---

# Change Rule

New auth-service metrics must be documented here before implementation.
