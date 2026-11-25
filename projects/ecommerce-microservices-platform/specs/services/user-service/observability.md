# user-service Observability

Defines business metrics specific to user-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `user_profile_created_total` | Counter | Total user profiles created (via IAM `account.created` event) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `user_profile_updated_total` | Counter | Total user profile updates — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `user_withdrawn_total` | Counter | Total user withdrawals — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `user_address_created_total` | Counter | Total shipping addresses created — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `user_address_deleted_total` | Counter | Total shipping addresses deleted — 🔵 *계획 · 미구현 (TASK-MONO-837)* |

---

# Change Rule

New user-service metrics must be documented here before implementation.
