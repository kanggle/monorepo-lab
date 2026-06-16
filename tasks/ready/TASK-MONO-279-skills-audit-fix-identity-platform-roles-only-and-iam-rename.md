# TASK-MONO-279 — Skill-library audit fixes: identity-platform-setup roles-only (ADR-032) + observability-query gap→iam rename residue

Status: ready

## Goal

Fix the two stale-content findings from the 2026-06-16 `.claude/skills/` audit. Both are documentation corrections that align skill files to **already-ACCEPTED** decisions / **already-completed** renames — no new policy, no behavior change, no ADR.

1. **`.claude/skills/service-types/identity-platform-setup/SKILL.md`** still teaches the `account_type (CONSUMER | OPERATOR)` partition that **ADR-MONO-032 removed** (roles became the sole identity axis; one account may hold consumer-facing AND operator-facing roles). The skill now contradicts its own prerequisite spec `platform/service-types/identity-platform.md` (fully migrated to roles-only) and the `platform/contracts/jwt-standard-claims.md` contract (`account_type` claim dropped). An agent following this skill would implement the removed model.

2. **`.claude/skills/cross-cutting/observability-query/SKILL.md`** (the fan-out trace tree, ~line 133) lists `├─ gap producer span` while the sibling spans are the current project names (`wms/scm/finance/erp`). `gap` was renamed to `iam` (`global-account-platform`→`iam-platform`); this is leftover rename residue.

## Scope

In one atomic PR (shared `.claude/skills/` paths — monorepo-level):

1. **`identity-platform-setup/SKILL.md`** — rewrite the `account_type` sections to the roles-only model, matching `platform/service-types/identity-platform.md` §§ "Consumer-facing capability", "Operator-facing capability", "One identity, both capabilities", "SSO Scope Rules", and `platform/contracts/jwt-standard-claims.md`. Specific anchors:
   - **Step 4 (Account domain)**: drop `account_type (CONSUMER | OPERATOR)` from the `Account` aggregate; capability is derived from **roles** (one account may hold both consumer-facing and operator-facing roles).
   - **Step 5 (Token issuance)**: remove `account_type` from the mandatory JWT claim list; the token carries `roles` (aud-scoped to the one target platform), per `jwt-standard-claims.md`.
   - **Step 9 (Social login)**: reframe "CONSUMER only" → social login is for the **consumer-facing capability** (consumer roles); operator-facing surfaces use local credentials / OIDC federation + MFA.
   - **Step 10 (SSO scope)**: replace "reject cross-type token requests" with role-possession-scoped SSO + per-`aud` least privilege; state explicitly there is **no cross-type SSO prohibition** (removed by ADR-032).
   - **"Account Type Enforcement Checklist"**: rewrite to role-presence / `aud`-scoping; remove the `Cross-type token request → unauthorized_client` item; keep the legitimate per-capability session-policy differences (consumer long refresh vs operator short refresh + MFA + audit) but key them off **role/capability**, not `account_type`.
   - **JWT-build code template** (`.claim("account_type", ...)`): replace with the roles-only claim set (drop `account_type`; emit `roles`).
   - **Gateway filter note** ("`account_type` enforcement as a reactive `GlobalFilter`"): reframe to role-based authorization.
2. **`observability-query/SKILL.md`** — change the one trace-tree line `├─ gap producer span` → `├─ iam producer span`.

Out of scope: any other skill (audit found the rest clean — 74/74 INDEX-consistent, no other rename residue). No `platform/`, `rules/`, agents, or commands edits.

## Acceptance Criteria

- `identity-platform-setup/SKILL.md` contains **zero** `account_type` references and no "reject cross-type" / `unauthorized_client`-for-cross-type language; its model matches `platform/service-types/identity-platform.md` (roles-only, one-account-both-capabilities) and `jwt-standard-claims.md` (no `account_type` claim).
- The legitimate operator-vs-consumer session/security differences (refresh lifetimes, MFA, social-login eligibility, audit) are preserved but expressed in terms of **roles/capability**.
- `observability-query/SKILL.md` trace tree reads `iam producer span`; no remaining `gap producer` reference. (The unrelated English "gap A" phrase elsewhere in the file is left untouched.)
- Both files still have valid frontmatter (`name`/`description` unchanged) and remain INDEX-consistent (no path/name change).
- No behavior/policy change — purely aligns docs to ADR-MONO-032 (ACCEPTED) + the completed gap→iam rename.

## Related Specs

- `platform/service-types/identity-platform.md` — authoritative roles-only model (target state for fix 1).
- `platform/contracts/jwt-standard-claims.md` — token claim contract (no `account_type`).
- ADR-MONO-032 (unified identity, roles-only) — the accepted decision the skill must match.
- `.claude/skills/INDEX.md` — must stay consistent (no entry change; names/paths unchanged).

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` (consumed as the claim-set authority; not modified).

## Edge Cases

- Operator-facing capability genuinely needs stricter session policy (short refresh, MFA, no social login, elevated audit) — this is **not** `account_type`; it is a per-capability/role rule. Keep it; only remove the `account_type` framing.
- `aud`-scoping still yields per-platform least privilege — the fix must preserve "one token = one platform's roles", not weaken it into "one token for everything".
- `observability-query` line 166 "gap A" is plain English (gap-analysis label), a false positive — do NOT change it.

## Failure Scenarios

- **Over-correction** — deleting the operator/consumer session-policy distinction along with `account_type`. The distinction is real and stays (keyed off roles/capability).
- **Under-correction** — leaving a stray `account_type` mention or the cross-type-rejection checklist item. AC requires zero `account_type` in the file.
- **`.claude/` commit gating** — editing `.claude/skills/` is per-action-approvable, but the *commit* of `.claude/` content may be classifier-gated. If the agent is blocked at commit time, hand the staged patch to the user to apply + commit (do not attempt a shell-write bypass). Per project memory `env_classifier_claude_self_mod_block`, `.claude/skills/` is softer than `.claude/{hooks,agents,commands}` (hard-blocked), so the edit itself should proceed under per-action approval.

## Notes

Surfaced by the 2026-06-16 `.claude/skills|agents|commands` audit (companion to the same-day CLAUDE.md rule-optimization MONO-278 and the memory-optimization pass). The audit found the library otherwise clean: 74 skills INDEX-consistent, 13 agents + 11 commands valid, dispatch catalogs (`config/{domains,traits,activation-rules}.md`) drift-free against `rules/` + `taxonomy.md`. These two are the only stale-content findings. Fix 1 is the same class of ADR-032 residue cleaned elsewhere in the per-domain SPEC alignment wave; the identity skill was simply missed because it lives under `.claude/`.
