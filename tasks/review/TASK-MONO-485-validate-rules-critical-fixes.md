# TASK-MONO-485 — `/validate-rules` (2026-07-29) Critical fixes

**Status:** review

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (mechanical doc/spec corrections, no design decisions — the authoritative value in each case is already established by cross-referencing an existing contract)

> Surfaced by the 2026-07-29 `/validate-rules` sweep. Same family as `TASK-MONO-410`/`TASK-MONO-413`
> (2026-07-15 sweep) — this task covers items those two did **not** touch (verified by diff against both
> tasks' before/after tables before filing this one, to avoid phantom/duplicate work).

---

## Goal

Fix 6 Critical findings — all either a direct contradiction between two normative documents, or a dangling
path reference. Each fix makes one document match the other document already established as authoritative.

## Scope

### In Scope — six independent fixes, one PR

1. **`platform/api-gateway-policy.md` line 50** — `X-User-Email (from email claim, if present)`. Per
   `platform/contracts/jwt-standard-claims.md` § Standard Claims, `email` is **Required: Yes** on every token.
   Drop "if present".
2. **`platform/api-gateway-policy.md` line 49** — `X-User-Role (from role or roles claim)`. The contract
   defines only `roles` (plural, array) as the authorization claim (ADR-MONO-032: "sole authorization axis");
   there is no singular `role` claim. Drop "role or" — `roles` only.
3. **`.claude/skills/frontend/implementation-workflow/SKILL.md` line 23** — references
   `feature-sliced-design.md` / `layered-by-feature.md`. Neither exists at that path. Fix to
   `frontend/architecture/feature-sliced-design/SKILL.md` and `frontend/architecture/layered-by-feature/SKILL.md`
   (the convention `frontend/component-library/SKILL.md` already uses correctly for the same two files).
4. **`.claude/skills/service-types/rest-api-setup/SKILL.md` line 18** — references
   `backend/architecture/{layered,clean,ddd,hexagonal}.md`. None of the four exist at that path (each lives at
   `backend/architecture/<name>/SKILL.md`). Fix all four to the `<name>/SKILL.md` form.
5. **`.claude/skills/backend/jwt-auth/SKILL.md` and `.claude/skills/service-types/identity-platform-setup/SKILL.md`**
   — both JWT-construction examples omit `tenant_id` and `tenant_type`, which
   `platform/contracts/jwt-standard-claims.md` § Standard Claims marks **Required: Yes on every grant**
   (enforced at the edge — `TenantClaimValidator` rejects a token missing `tenant_id`). Add both claims to each
   example, citing the contract. (Confirmed not already covered by `TASK-MONO-410`'s jwt-auth rewrite — that
   task added `roles`/RS256/JWKS but never touched tenant claims; re-verified 2026-07-29 via direct grep, zero
   matches in either file.)
6. **`platform/event-driven-policy.md` line 76** — "rows are deleted from outbox only after broker
   acknowledgment." This is now stale: `TASK-MONO-413` (2026-07-15) already rewrote
   `.claude/skills/messaging/outbox-pattern/SKILL.md` to correctly teach the *actual* implementation
   (`AbstractOutboxPublisher` / `OutboxRow.markPublished()` — rows are marked published, never deleted) and
   explicitly logged this exact spec-side correction as a deferred follow-up ("스펙 쪽 후속 후보로 넘김") that
   was never filed. Update the spec sentence to describe mark-published semantics, matching the skill and the
   live library code.
7. **`.claude/commands/process-tasks.md`** — the architecture diagram (`Merge → approved tasks move to
   done/, fix tasks created in ready/`) and the Phase 3 summary template field (`Tasks completed (done):
   [count]`) both contradict the command's own accurate Phase 2 procedure text (steps 5 and 7: review never
   moves the task file; `review/ → done/` is a separate close-chore PR gated on 3-dimension merge verification).
   Fix the diagram line and rename/reframe the summary field to reflect "approved, pending close chore" rather
   than "done".

### Out of Scope

- Anything already fixed by `TASK-MONO-410`/`TASK-MONO-413` (re-verified not to overlap before filing).
- Warning/Info-tier findings from the same sweep — tracked separately (`TASK-MONO-486`/`487`/`488`).
- `.claude/skills/backend/external-http-integration/SKILL.md`'s `catch (Exception e)` — the sweep flagged this,
  but `TASK-MONO-413` § AC-5 already reviewed this exact file and explicitly decided **not** to narrow it
  (legitimate best-effort outbound-delivery boundary, already commented as such) — not re-litigated here.
- Any production code change. Every fix in scope is doc/spec/skill text only.

---

## Acceptance Criteria

- **AC-0 (gate)** — Before editing, re-confirm each of the 7 items above is still present exactly as described
  (grep/read the live file). Drop any that turn out already fixed and say so in the PR body.
- **AC-1** — `api-gateway-policy.md` lines 49–50 match the claims contract exactly (`roles` only, `email`
  unconditional).
- **AC-2** — Both dangling-path skills (`frontend/implementation-workflow`, `service-types/rest-api-setup`)
  resolve to real files; re-glob after edit to confirm 0 dangling.
- **AC-3** — `jwt-auth/SKILL.md` and `identity-platform-setup/SKILL.md` both include `tenant_id` and
  `tenant_type` in their JWT example payloads, with a citation to `jwt-standard-claims.md`.
- **AC-4** — `event-driven-policy.md`'s outbox sentence matches `markPublished()` semantics (no deletion),
  consistent with the already-corrected skill.
- **AC-5** — `process-tasks.md`'s diagram and summary template no longer imply the pipeline moves tasks to
  `done/`; wording matches the accurate Phase 2 procedural text already in the same file.
- **AC-6** — No new dangling references introduced by any edit (spot-check every path touched).

## Related Specs

- `platform/contracts/jwt-standard-claims.md`, `platform/api-gateway-policy.md`, `platform/event-driven-policy.md`
- `.claude/commands/review-task.md` § Close Chore (the accurate close-chore procedure `process-tasks.md`
  must stay consistent with)
- Prior art: `tasks/done/TASK-MONO-410-security-skills-teach-the-opposite-of-the-contract.md`,
  `tasks/done/TASK-MONO-413-skill-spec-drift-sweep.md`

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` (claims 5, 6 read this contract; do not change the contract
  itself — the skills/spec-prose are what's wrong, not the contract).

## Edge Cases

- Item 6 changes normative spec prose (not just a skill), so double-check no other file quotes the old
  "deleted... after broker acknowledgment" sentence verbatim (grep the exact phrase repo-wide before/after).
- `.claude/skills/**` and `platform/**` and `.claude/commands/**` are not classifier-blocked paths — proceed
  without pre-emptive hand-off; only `.claude/hooks/`+`.claude/settings.json` are.

## Failure Scenarios

- **F1** — fixing an item already resolved by `TASK-MONO-410`/`413`, producing churn/re-drift. Guarded by AC-0
  and the explicit prior-task diff already done at filing time.
- **F2** — the outbox spec fix (item 6) drifts from the skill's actual wording instead of matching it. Guarded
  by AC-4 requiring direct comparison against the already-corrected skill text.
