# Task ID

TASK-BE-299

# Title

GAP admin-web parity-gated retirement (ADR-MONO-013 Phase 3 — spec-first app removal + console absorption)

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
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

- **depends on**: ADR-MONO-013 (ACCEPTED) § D4 (parity-gated retirement) + § D6 Phase 3 (gate = "Phase 2 parity verified"). The gate is **satisfied**: ADR-MONO-013 § 6 additive note + `projects/platform-console/specs/contracts/console-integration-contract.md` § 3 (16/16 verified parity matrix, programmatically attested by `apps/console-web/tests/unit/parity-verification.test.ts`); the operator surface was absorbed by platform-console FE-002…FE-006 + GAP-side TASK-BE-296 (OIDC client + registry) / TASK-BE-298 (operator token exchange), all merged.
- **prerequisite for**: nothing (terminal step of the ADR-MONO-013 admin-web arc; GAP becomes backend-only IdP).
- **spec-first**: GAP spec changes (PROJECT.md service map + `specs/services/admin-web/` + README + migration-notes) land **before / together with** the `apps/admin-web/` deletion in the same atomic PR; the app is removed only after its spec is converted to a retirement record (ADR-MONO-013 § D4.3).
- **cross-project ripple (one atomic PR — CLAUDE.md § Cross-Project Changes)**: removing GAP's only `frontend-app` deterministically invalidates monorepo-level shared files (`package.json` dead `gap:*` JS scripts, root `README.md` GAP row, `docs/project-overview.md` GAP service map). This is a *consequence* authorized by the monorepo-level ACCEPTED **ADR-MONO-013 § D6 Phase 3** (decision authority), executed under the GAP-scoped task the ADR explicitly mandates ("Location: GAP `tasks/` (spec-first)"); the shared adaptation lands atomically to avoid a transiently broken main. No new monorepo decision is taken here.

---

# Goal

Execute **ADR-MONO-013 Phase 3**: retire GAP `admin-web` now that the platform-console has reached **verified operator parity** (Phase 2 = COMPLETE, 5/5 slices). Per ADR-MONO-013 § D4 retirement is **not a silent delete** — it is a recorded, parity-gated deprecation:

1. The console absorbed `admin-web`'s entire operator surface (accounts lock/unlock/bulk-lock/revoke-session/gdpr-delete/export · audit · security login-history/suspicious · operators create/edit-roles/change-status/password · operator overview). Parity is **verified** (16/16 matrix, programmatic).
2. GAP `PROJECT.md` service map drops the `frontend-app` service type and **records console absorption**; the `specs/services/admin-web/` spec is converted to a **retirement record** (tombstone) pointing at platform-console + ADR-MONO-013/014/015; GAP README + migration-notes record the change.
3. **Then** the `apps/admin-web/` deployable unit is removed.

After this task: **GAP is backend-only** (IdP + service APIs), exactly as ADR-MONO-013 § D2/§ 3.3 predicted; the only GAP operator UI is the platform-console; no live operator capability is lost (parity pre-verified, not asserted here).

# Scope

## In Scope

### Spec-first (GAP project-internal — lands before/with the app deletion)

- `projects/global-account-platform/PROJECT.md`
  - Frontmatter `service_types`: drop `frontend-app` (admin-web was the **only** `frontend-app`; community/membership are `rest-api`, frozen). Result: `[rest-api, event-consumer, identity-platform]`.
  - Add a concise **admin-web retirement record** near the Service Map: retired 2026-05-18, operator surface absorbed by `projects/platform-console/` (Model B), parity verified (ADR-MONO-013 § 6 + console-integration-contract § 3), governed by ADR-MONO-013 § D4/§ D6 Phase 3 + ADR-MONO-014/015. GAP is now backend-only.
- `projects/global-account-platform/specs/services/admin-web/`
  - `overview.md` → **rewrite into a `RETIRED` record** (tombstone at the conventional spec entry path): one screen — retired date, reason (Model B console absorption), parity-verification pointer, ADR references, the absorbing slices (PC-FE-002…006 + BE-296/298), and a "git history preserves the original architecture/dependencies/observability specs" note.
  - `architecture.md`, `dependencies.md`, `observability.md` → `git rm` (they describe a now-nonexistent deployable; git history retains them; the retirement record is the single forward pointer). No other GAP spec path-links **into** these files (verified: zero inbound `services/admin-web/` links from sibling specs — cross-refs were admin-web → siblings, not siblings → admin-web).
- `projects/global-account-platform/README.md` — remove admin-web from: the ASCII architecture diagram (Admin Web box), Tech Stack "Frontend" row, Services table row, Prerequisites (Node.js), the "### 3. Frontend" quick-start section, Repository Structure tree. Add a short "Operator console" pointer line: operator UI is now `projects/platform-console/` (ADR-MONO-013 Phase 3 retirement). Keep backend narrative intact.
- `projects/global-account-platform/docs/migration-notes.md` — append a dated section "## admin-web retirement (2026-05-18 · ADR-MONO-013 Phase 3 · TASK-BE-299)" recording the spec change → app removal → console absorption + the parity-verification reference (GAP-local changelog).

### App removal (after the spec becomes a retirement record)

- `git rm -r projects/global-account-platform/apps/admin-web/` (entire deployable unit incl. `tests/e2e/*.spec.ts`).
- `git rm projects/global-account-platform/pnpm-workspace.yaml projects/global-account-platform/pnpm-lock.yaml` — admin-web was the sole pnpm package; GAP becomes a pure Java/Gradle project (no JS workspace remains).

### Monorepo-level shared ripple (atomic, same PR — ADR-MONO-013 § D6 Phase 3 authorizes; CLAUDE.md § Cross-Project Changes one-atomic-PR)

- Root `package.json`: remove the now-dead GAP JS scripts that require a GAP pnpm workspace — `gap:install`, `gap:dev`, `gap:build`, `gap:lint`, `gap:admin-web`, `gap:pnpm`. Keep the docker-compose ones (`gap:up/bootrun/down/ps/logs/docker`) — those operate the backend stack and are unaffected.
- Root `README.md` (portfolio hub): GAP project row — "5 backend services … + admin-web frontend" → backend-only; note operator UI absorbed by platform-console (ADR-MONO-013 Phase 3).
- `docs/project-overview.md` (portfolio narrative SoT): § 2.2 line "5 backend + admin-web 운영 진입" → backend-only; "service map (5 active + 1 frontend + 2 frozen demo)" → "(5 active + 2 frozen demo)"; remove the `admin-web | frontend-app` service-map row; § 2.6/2.x platform-console line "GAP `admin-web`은 … Phase 3 폐기" → mark Phase 3 **DONE (2026-05-18)** / GAP backend-only IdP 회귀 완료.

## Out of Scope

- Any change to platform-console (it already has full parity — FE-001…006 merged; nothing to add to retire admin-web).
- Any GAP backend service behavior (auth/account/admin/security/gateway) — admin-web is a thin consumer; its removal touches **no** backend code, contract, Gradle module, or migration.
- ADR text edits — ADR-MONO-013 § 6 already carries the additive "Phase 2 parity verified / Phase 3 gate satisfied" note (TASK-PC-FE-006); this task **executes** the gated retirement, it does not re-decide or amend any ADR (HARDSTOP-04 discipline; ADR-MONO-013 stays ACCEPTED, decisions D1–D8 unchanged).
- Historical task files under `tasks/done/` referencing admin-web — **immutable** (INDEX.md "Do not modify a task file after it moves to done/"); they are an audit trail, left verbatim. Only **live** spec/config/script references are updated.
- CI workflow edits — verified: **zero** `admin-web` references in `.github/workflows/*`; admin-web is not a Gradle module and has no GAP frontend CI job; the `ci.yml` GAP path-filter is glob-based (no admin-web literal). Removing the app needs no workflow change.

# Acceptance Criteria

- **AC-1 (parity gate, precondition)**: ADR-MONO-013 § D6 Phase 3 gate "Phase 2 parity verified" is documented satisfied (ADR § 6 additive note + console-integration-contract § 3 16/16 + `parity-verification.test.ts`). This task does not re-verify parity; it records the dependency and proceeds.
- **AC-2 (spec-first ordering)**: in the produced diff, GAP `PROJECT.md` + `specs/services/admin-web/overview.md` (retirement record) + README + migration-notes reflect the retirement; the `apps/admin-web/` deletion is part of the **same** commit/PR (atomic) — the spec is never left describing a deleted app, and the app is never deleted without its retirement record.
- **AC-3 (no live dangling reference)**: post-change, `git grep -l "admin-web"` returns **only** (a) immutable `tasks/done/**` historical records, (b) the deliberate retirement records (GAP `PROJECT.md` note, `specs/services/admin-web/overview.md` tombstone, GAP README pointer, GAP migration-notes section, root README/`project-overview.md` retirement notes), and (c) ADR-MONO-013/014/015 (which describe the retirement). **No** live build/spec/script/workspace reference to a runnable admin-web remains.
- **AC-4 (GAP backend-only)**: GAP `PROJECT.md` `service_types` no longer contains `frontend-app`; `pnpm-workspace.yaml`/`pnpm-lock.yaml`/`apps/admin-web/` are gone; the only remaining JS in GAP is none (pure Java/Gradle).
- **AC-5 (build integrity)**: `settings.gradle` is unaffected (admin-web is not a Gradle module — verify it never appeared there); `./gradlew :projects:global-account-platform:...:check` task graph is unchanged (no admin-web Gradle target existed). Root `package.json` is valid JSON after script removal. The PR's CI: backend `Build & Test` + `Integration (global-account-platform, Testcontainers)` unaffected (frontend-only deletion cannot break Gradle/Testcontainers); doc/json/code path-filter triggers the full pipeline but no admin-web-specific job exists.
- **AC-6 (retirement is recorded, not silent)**: the retirement is discoverable from three independent anchors — GAP `PROJECT.md` (canonical service map, ADR-MONO-013 § D4.3), GAP `docs/migration-notes.md` (GAP changelog), and the `specs/services/admin-web/overview.md` tombstone — each pointing to platform-console + the governing ADRs.
- **AC-7 (governance)**: task lives in GAP `tasks/ready/` (ADR-MONO-013 § D6 Phase 3 mandated location); the monorepo-level shared ripple is included atomically and labelled, with ADR-MONO-013 § D6 as the cited decision authority (no separate root task — that would fragment the atomic cross-project change).

# Related Specs

- [ADR-MONO-013 § D4 / § D6 Phase 3 / § 6 additive note](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — governing decision (parity-gated retirement; gate satisfied).
- [ADR-MONO-014](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md), [ADR-MONO-015](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) — operator-auth bridge + dashboards model that made absorption complete.
- [projects/global-account-platform/PROJECT.md](../../PROJECT.md) — service map mutated here (ADR-MONO-013 § D4.3 / § 3.3).
- [projects/platform-console/specs/contracts/console-integration-contract.md § 3](../../../platform-console/specs/contracts/console-integration-contract.md) — verified parity matrix (the gate evidence).
- `projects/global-account-platform/specs/services/admin-web/{overview,architecture,dependencies,observability}.md` — overview → retirement record; other three removed.

# Related Contracts

- None changed. admin-web is a **consumer** of `specs/contracts/http/admin-api.md` / `auth-api.md` / `console-registry-api.md`; it owns no contract or persisted state (per its own architecture.md § Integration Rules). Removing the consumer changes no producer contract. Backend contracts are untouched.

# Edge Cases

- **Inbound spec links into `services/admin-web/`**: verified zero from sibling GAP specs (the cross-refs were admin-web → siblings). The `overview.md` tombstone is retained at the conventional path so any *historical task* link still resolves to a meaningful retirement record rather than a 404.
- **`pnpm-lock.yaml` removal**: no regeneration needed — there is no remaining GAP JS package; the file is deleted, not rebuilt. No `pnpm install` is required (and none is run; local env has no Node guarantee).
- **Root `package.json` script removal**: only the JS-workspace-dependent `gap:*` scripts are removed; docker-compose `gap:*` scripts stay valid (they operate the backend stack, no Node). JSON validity re-checked.
- **Historical task files**: 40+ `tasks/done/TASK-FE-*` / `TASK-BE-275/290` reference admin-web — intentionally **not** touched (immutable audit trail; INDEX.md rule). AC-3's grep allow-list explicitly permits `tasks/done/**`.
- **CI markdown/code split**: this PR mixes code deletion + md + json. The path-filter classifies it as `code-changed` → full pipeline. That is expected and harmless (no admin-web Gradle/Testcontainers/e2e job exists to fail).

# Failure Scenarios

- **A stale live reference is missed** → a build script or spec points at a deleted path. Mitigation: AC-3 exhaustive `git grep` with an explicit allow-list (historical tasks + deliberate retirement records + ADRs); anything else is a finding → fix in-PR before review approval.
- **Spec describes a deleted app (ordering violation)** → AC-2 requires the retirement record + deletion in one atomic commit; reviewer rejects if `apps/admin-web/` is gone but `specs/services/admin-web/overview.md` still describes it as live (or vice-versa).
- **Accidental backend regression** → impossible by construction (admin-web is not a Gradle module, owns no contract/persistence); AC-5 still gates on the backend CI jobs being green to prove the frontend-only deletion did not perturb the build graph.
- **Governance drift (treated as plain monorepo chore)** → would lose the parity-gated, recorded-retirement discipline ADR-MONO-013 § D4 mandates. Mitigation: AC-6 (three recorded anchors) + AC-7 (GAP-scoped task, ADR-cited authority, atomic ripple) — the retirement is auditable, not a silent delete.

# Verification

1. Spec-first diff inspection: `PROJECT.md` service_types/`frontend-app` dropped + retirement note present; `specs/services/admin-web/overview.md` is a RETIRED record; `architecture.md`/`dependencies.md`/`observability.md` deleted; README/migration-notes updated — all in the same commit as the `apps/admin-web/` deletion.
2. `git grep -n "admin-web"` → every hit is in the AC-3 allow-list (historical `tasks/done/**`, the retirement records, ADR-MONO-013/014/015). Zero live runnable references.
3. `git grep -n "admin-web" settings.gradle` → empty (was never a Gradle module); `node -e "require('./package.json')"` / JSON parse → root `package.json` valid; removed scripts are exactly the 6 JS-workspace ones.
4. CI: backend `Build & Test (JDK21)` + `Integration (global-account-platform, Testcontainers)` green on the PR (proves frontend deletion did not perturb the Gradle/IT graph). No admin-web job exists by design.
5. Retirement discoverable from GAP `PROJECT.md` + `docs/migration-notes.md` + `specs/services/admin-web/overview.md` tombstone, each → platform-console + ADR-MONO-013 Phase 3.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (destructive parity-gated retirement; spec-first ordering + cross-project atomic ripple + governance/ADR-citation judgement — not a mechanical delete) / 리뷰=Opus 4.7 (inline self-review, review-checklist).
