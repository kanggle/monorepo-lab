# Task ID

TASK-BE-424

# Title

account-service bulk-provisioning javadoc — BE-257 stale `TODO` reality-alignment (code mirror of BE-316's admin-events.md fix)

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-BE-316 reality-aligned the **spec** side (`admin-events.md:L57`): `ACCOUNT_BULK_CREATE`
has no emitter by deliberate TASK-BE-257 design (bulk provisioning emits per-row
`account.created`, not a bulk `admin.action.performed` envelope), and dropped the `(TODO)`.
But BE-316 was scoped to the spec only — the **code** javadoc still carries the
pre-BE-257 stale prose plus a `TODO(TASK-BE-257): wire admin-service audit event …` marker
in two account-service files. The marker violates `platform/coding-rules.md` ("no `TODO`
promising de-scoped work") and re-asserts a future emission that BE-257 explicitly chose
against. This task lands the code-side mirror of BE-316's Option A: rewrite the javadoc to
state the finalised design and drop the `TODO` (not a bare line-delete that would leave the
stale future-tense prose).

**Stale markers (verified)**:

- `apps/account-service/.../application/service/BulkProvisionAccountUseCase.java` — javadoc "Audit obligation (admin-service)" + `TODO(TASK-BE-257)`.
- `apps/account-service/.../presentation/internal/BulkAccountController.java` — javadoc "Audit emission (admin-service)" + `TODO(TASK-BE-257)`.

Reality: BE-257 (`done/`) finalised per-row `account.created` emission + the bulk-call audit
in `account_status_history`; admin-service has no `ACCOUNT_BULK_CREATE` emitter and the
actionCode is kept only as a forward-compatible reserved enum entry (admin-events.md, aligned
by BE-316 `done/`).

# Scope

## In Scope

- `BulkProvisionAccountUseCase.java` javadoc: rewrite the audit paragraph to BE-257 finalised design; remove the `TODO(TASK-BE-257)` marker.
- `BulkAccountController.java` javadoc: same rewrite + `TODO` removal.

## Out of Scope

- Any behaviour change — javadoc/comment only; no method body, signature, or wiring touched.
- Adding an admin-service `ACCOUNT_BULK_CREATE` emitter (a separate BE-NNN if business demand surfaces; BE-257 de-scoped it).
- `admin-events.md` (already aligned by BE-316).
- The `TASK-BE-257:` *attribution* javadoc lines on the other bulk-provisioning classes (Command/Result/Request/Response/Exception) — those are descriptive provenance, not `TODO`s.

# Acceptance Criteria

- [ ] `grep -rn "TODO(TASK-BE-257)"` under `apps/account-service/src/main/java` returns 0 results.
- [ ] Both rewritten javadocs state the BE-257 finalised design (per-row `account.created`; `account_status_history` bulk audit; reserved-but-unemitted `ACCOUNT_BULK_CREATE`) and reference BE-316 for the spec alignment.
- [ ] No future-tense "should be emitted / future emission once pattern established" prose remains in either file.
- [ ] No production-code behaviour change (javadoc only); `:account-service:compileJava` + `:test` GREEN.

# Related Specs

- `projects/iam-platform/specs/contracts/events/admin-events.md` (authoritative, aligned by BE-316 — unchanged here)

# Related Contracts

- None — javadoc-only; no wire/contract surface touched.

# Edge Cases

- Keep the `account_status_history` bulk-audit-row description already present in the BulkProvisionAccountUseCase javadoc — the rewrite must not duplicate or contradict it.
- Do not remove the descriptive `TASK-BE-257:` provenance lines on sibling DTO/exception classes — only the two `TODO(...)` markers + their stale future-tense prose.

# Failure Scenarios

- **F1 — bare TODO delete (Option C)**: stripping only the `TODO(...)` line leaves "should be emitted once pattern established" future-tense prose → re-surfaces as a finding next audit. Guarded by rewriting the full paragraph.
- **F2 — accidental behaviour edit**: this is comment-only; any change under method bodies is out of scope and a review-blocker.
