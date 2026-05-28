# Task ID

TASK-MONO-149

# Title

/validate-rules remediation — fix 3 Critical (coordinator agent-glob, implement-task template path, grpc dead-ref) + 4 Warning (gateway-as-Service-Type, two error-code HTTP collisions, stale task-id allowlist) rule-file inconsistencies surfaced by the 2026-05-29 full-scan

# Status

ready

# Owner

monorepo (root tasks/ — shared `platform/` + `.claude/` governance files)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

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

# Dependency Markers

- **origin**: `/validate-rules` full scan 2026-05-29 (read-only audit) reported 3 Critical + 4 Warning + 6 Info across `platform/` and `.claude/`. All 3 Critical and all 4 Warning were independently re-verified against the actual file bytes before this task was written. Info items are out of scope.
- **prerequisite for**: nothing (governance hygiene).
- **spec-first**: spec PR (this task md + INDEX ready entry, no rule-file edit) → impl PR (the 7 rule-file edits, ready→review) → close chore PR (review→done + INDEX).
- **model**: 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical doc fixes, low complexity, no behavior) — dispatcher-direct acceptable / 리뷰=Opus 4.7.

---

# Goal

Bring the shared rule library back into internal consistency after the 2026-05-29 `/validate-rules` scan. Each fix is a surgical, behavior-free documentation correction: repair two dead/contradictory path references in `.claude/`, one dangling spec link and one mis-categorised Service Type in `platform/`, two same-code/different-HTTP-status annotations in `platform/error-handling.md`, and one stale task-id allowlist in `platform/naming-conventions.md`. No code, no ADR, no behavior. Shared files MUST stay project-agnostic (HARDSTOP-03).

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No rule-file edit.

**Impl PR** — exactly these 7 edits:

**Critical**

1. `.claude/agents/common/coordinator.md` — the agent-frontmatter scan glob `.claude/agents/*.md` matches 0 files (agents live under `common/`). Correct to `.claude/agents/common/*.md` (+ `domain/**/*.md` when domain agents exist).
2. `.claude/commands/implement-task.md` — the Agent Prompt Template `## Task` header reads `Task file: tasks/in-progress/{taskFileName}` but Step 2 of the same template reads from `tasks/ready/{taskFileName}` (the task is in `ready/` at dispatch; Step 3 moves it). Correct the header path to `tasks/ready/{taskFileName}`.
3. `platform/service-types/grpc-service.md` — `See infra/service-mesh.md for mesh-managed mTLS` is a dangling link (`infra/` holds only `traefik/` + `observability/`). Reword to remove the dead path while preserving the mTLS-required rule (project-agnostic; no gRPC service exists yet).

**Warning**

4. `platform/service-boundaries.md` — the "§ Service Type Boundaries" table (keyed by `Service Type`) lists a `gateway` row, but `gateway` is not a catalog Service Type; `platform/api-gateway-policy.md` + every gateway `architecture.md` declare `rest-api`. Relabel the row to make clear gateway is the gateway *role* of a `rest-api` service.
5. `platform/error-handling.md` — `INSUFFICIENT_STOCK` is 422 (wms Inventory) and 400 (ecommerce Product) for two different operations. Add a reciprocal cross-domain note to each entry (matching the existing cross-domain-reuse annotation convention, e.g. admin `CIRCUIT_OPEN`).
6. `platform/error-handling.md` — `DOWNSTREAM_ERROR` is 502 (Platform-Common) and 503 (Admin/saas). The 503 side already documents the split; add the reciprocal note on the 502 entry so the divergence is symmetric.
7. `platform/naming-conventions.md` — the task-id allowlist `TYPE is BE, FE, or INT` is stale (repo uses `MONO` + project prefixes + letter sub-task suffixes). Generalise to `SCOPE` (project-agnostic — do NOT enumerate project prefixes) and point to `tasks/INDEX.md` as the authoritative registry.

## Out of Scope

- **The 6 Info findings** (agent `skills:` frontmatter unevenness; implement-task↔write-tests test-authoring overlap; `refactor-spec.md` `sed -i`; `refactoring-policy.md` entrypoint trigger; error-handling ACCOUNT_LOCKED/DORMANT TODO; placeholder service-type disclaimers) — deliberately deferred (design-tension / optional-field / external-implementation-gated, not clear defects).
- **Any code, ADR, or behavior change** — renaming an emitted error code (e.g. admin `DOWNSTREAM_ERROR` → 503) would require source changes and is explicitly NOT done; fix 5/6 are annotation-only.
- **Project-specific content in shared files** — fixes 3 and 7 stay project-agnostic (HARDSTOP-03).

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land in a spec PR with no rule-file edit.
- **AC-2 (Critical resolved)**: `.claude/agents/common/coordinator.md` scans `common/*.md`; `implement-task.md` template header reads `tasks/ready/`; `grpc-service.md` has no `infra/service-mesh.md` link (and no other dangling link introduced).
- **AC-3 (Warning resolved)**: `service-boundaries.md` no longer presents `gateway` as a standalone Service Type; both `INSUFFICIENT_STOCK` entries and the 502 `DOWNSTREAM_ERROR` entry carry a cross-domain note; `naming-conventions.md` task-id rule is generalised + points to `tasks/INDEX.md`.
- **AC-4 (project-agnostic preserved, HARDSTOP-03)**: the `platform/` edits introduce no service name, API path, or project prefix; `git diff origin/main` of shared files contains no project-specific tokens.
- **AC-5 (scope-lock)**: `git diff origin/main` touches only the 7 named rule files (+ the task lifecycle files); no code/ADR/spec-contract change.
- **AC-6 (re-run clean on fixed dimensions)**: a follow-up `/validate-rules` would report the 3 Critical + 4 Warning as resolved (the dead glob/path/link gone, the Service Type catalog conflict gone, the error-code collisions annotated, the task-id allowlist current).

# Related Specs

- `.claude/agents/common/coordinator.md`, `.claude/commands/implement-task.md` — the two `.claude/` files corrected.
- `platform/service-types/grpc-service.md`, `platform/service-boundaries.md`, `platform/error-handling.md`, `platform/naming-conventions.md` — the four `platform/` files corrected.
- `platform/service-types/INDEX.md`, `platform/api-gateway-policy.md` — the authoritative catalog + gateway-type rule that fix 4 aligns to.
- `tasks/INDEX.md` — the authoritative task-id registry fix 7 points to.

# Related Contracts

- None. Governance documentation only.

# Edge Cases

- **Two `rest-api` rows** — after fix 4 the boundaries table may show both `rest-api (gateway role)` and `rest-api`; that is intentional (gateway role carries an extra "no business logic" constraint).
- **error-code annotation, not rename** — fix 5/6 must NOT change any HTTP status or code string (that would be a behavior/code change); they only add descriptive notes.
- **HARDSTOP-03** — fix 7 must describe the scheme generically (`MONO`, work-type, "project-specific prefix") without naming any actual project prefix; fix 3 must not name a project.

# Failure Scenarios

- **Code/ADR/behavior edited** → AC-5 fail; governance-doc-only.
- **A project token lands in a `platform/` file** → AC-4 fail (HARDSTOP-03).
- **An error code string or HTTP status changed** → out-of-scope behavior change; revert to annotation-only.
- **A new dangling link introduced by the grpc reword** → AC-2 fail.

# Verification

1. Spec PR: this md + INDEX ready entry; no rule-file edit.
2. Impl PR: 7 named files only; `git diff origin/main` scope + project-agnostic check; no new dangling links.
3. CI `changes` fast-lane (docs/task) GREEN.
4. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical rule-file remediation) / 리뷰=Opus 4.7 (AC-2/3 resolved + AC-4 HARDSTOP-03 project-agnostic + AC-5 scope-lock + BE-303 3-dim).
