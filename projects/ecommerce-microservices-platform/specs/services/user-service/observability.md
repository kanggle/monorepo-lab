# user-service Observability

Defines business metrics specific to user-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `user_profile_created_total` | Counter | Total user profiles created (via UserSignedUp event) |
| `user_profile_updated_total` | Counter | Total user profile updates |
| `user_withdrawn_total` | Counter | Total user withdrawals |
| `user_address_created_total` | Counter | Total shipping addresses created |
| `user_address_deleted_total` | Counter | Total shipping addresses deleted |

---

# Change Rule

New user-service metrics must be documented here before implementation.
