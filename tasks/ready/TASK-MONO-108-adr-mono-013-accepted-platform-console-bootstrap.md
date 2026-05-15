# Task ID

TASK-MONO-108

# Title

ADR-MONO-013 ACCEPTED transition + platform-console Phase 1 bootstrap

# Status

ready

# Owner

architecture

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr
- code
- deploy

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

Execute the ADR-MONO-013 ACCEPTED transition (user-explicit intent "ADR-013 ACCEPTED", 2026-05-16) and Phase 1 of its § D6 roadmap.

After this task:

- ADR-MONO-013 is ACCEPTED (Status + History + D3 finalised + § 6 audit-trail rows), memory reflects ACCEPTED.
- `projects/platform-console/` exists as a monorepo direct-include project (`PROJECT.md` per D3, `frontend-app` skeleton, first project task in its `tasks/ready/`), wired into `settings.gradle` / root `package.json` / Traefik `console.local`.
- GAP exposes the console OIDC client + a product/tenant registry surface the console catalog will read (GAP project-internal, spec-first).
- The cross-project BFF integration contract is authored as a spec (skeleton in ADR § D5 → full spec here).

# Scope

## In Scope

### PR-A — doc-only (ACCEPTED recording)
- ADR-MONO-013 `PROPOSED → ACCEPTED` (Status, History, D3 FINALISED, § 6 PROPOSED row PR=#565 + ACCEPTED row + D7.1–D7.6 evaluation).
- This task file (`tasks/ready/`).
- Memory `project_platform_console_adr_013` + `MEMORY.md` pointer → ACCEPTED.

### PR-B — bootstrap artifact
- `projects/platform-console/PROJECT.md` — `domain: saas` / `traits: [multi-tenant, integration-heavy, audit-heavy]` / `service_types: [frontend-app]` (ADR-MONO-013 D3).
- `projects/platform-console/apps/console-web/` — `frontend-app` skeleton (Next.js, same stack family as GAP `admin-web` / ecommerce `web-store`): GAP OIDC login (Auth Code + PKCE), data-driven service catalog shell, tenant switcher placeholder. No domain screens yet (Phase 2+).
- `projects/platform-console/specs/contracts/` — BFF cross-project integration contract spec (ADR § D5 skeleton → full: OIDC client reg, tenant/product registry shape, console-facing read/ops API expectations, resilience baseline).
- `projects/platform-console/tasks/ready/TASK-PC-FE-001-...` — first project task (console shell + GAP OIDC login), implementation deferred.
- Monorepo wiring: `settings.gradle` (if a Gradle module is needed for a future bff; frontend-only app may not need it — decide at impl), root `package.json` shortcut, `docker-compose` Traefik `Host(console.local)` label, `.env.example`.
- GAP project-internal: console OIDC client registration (GAP seed/migration) + product/tenant registry surface (spec-first in GAP `specs/`, then a GAP task `tasks/ready/`).
- `docs/project-overview.md` § 2 — add `platform-console` as 6th project; § 2.6 unaffected (finance/erp still 미생성).

## Out of Scope

- Console domain screens / GAP operator parity (Phase 2).
- `admin-web` removal (Phase 3, parity-gated).
- wms/scm/finance/erp sections (Phase 4–6).
- console-bff aggregation (Phase 7).
- finance/erp taxonomy or `rules/domains/<d>.md` (governed by ADR-MONO-008 / future erp ADR — NOT here).
- Template-fork flow (`gh repo create --template`): this is a monorepo direct-include bootstrap (ADR-MONO-013 § 1.5), not the ADR-MONO-008 Template demo.

# Acceptance Criteria

- [ ] ADR-MONO-013 Status = ACCEPTED; § 6 has PROPOSED(#565) + ACCEPTED rows; D3 has no "recommended/proposed" hedging.
- [ ] `projects/platform-console/PROJECT.md` parses with the D3 frontmatter; classification verifiable against `rules/taxonomy.md` + `.claude/config/`.
- [ ] `console-web` skeleton builds (lint/build) and boots a placeholder shell behind GAP OIDC.
- [ ] BFF integration-contract spec exists under `projects/platform-console/specs/contracts/`.
- [ ] `console.local` resolves to the console via Traefik label (compose), per TEMPLATE.md Local Network Convention.
- [ ] GAP console OIDC client + product/tenant registry surface specced (spec-first) + GAP task authored.
- [ ] `docs/project-overview.md` lists `platform-console` (project count 5 → 6).
- [ ] `./gradlew projects` / frontend build green; CI path-filter classifies correctly.
- [ ] memory + `MEMORY.md` reflect ACCEPTED + bootstrap.

# Related Specs

> Monorepo-level + new project. PR-B follows `platform/entrypoint.md`; `platform/service-types/frontend-app.md` is the governing service-type contract for `console-web`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` (§ D3, D5, D6, D8)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D2.1 (churn-clock; record consequence)
- `platform/service-types/frontend-app.md`
- `TEMPLATE.md` § Local Network Convention (`console.local`)
- `projects/global-account-platform/PROJECT.md` + `specs/features/multi-tenancy.md` (OIDC client + tenant registry integration point)
- `projects/global-account-platform/apps/admin-web/` (frontend-app skeleton reference; stack parity)
- `docs/done/TASK-MONO-040-scm-platform-bootstrap.md` (new-project bootstrap procedure precedent)

# Related Skills

- `.claude/skills/` — frontend-app bootstrap + ADR ACCEPTED recording (ADR-MONO-008 D6.2 / TASK-MONO-070 precedent).

---

# Related Contracts

- New: `projects/platform-console/specs/contracts/` BFF cross-project integration contract (authored in PR-B; skeleton = ADR-MONO-013 § D5).
- GAP: console OIDC client registration + product/tenant registry surface (GAP `specs/` change, spec-first).

---

# Target Service

- New project `platform-console` / `apps/console-web` (frontend-app).
- GAP (project-internal): OIDC client + registry surface.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` and the GAP `admin-web` / ecommerce `web-store` Next.js pattern (stack parity, OIDC Auth Code + PKCE via GAP).
- Console = Model B single UI (ADR-MONO-013 D1); no per-domain UI; domain screens render via gateway/admin APIs (Phase 2+).

---

# Implementation Notes

- 2-PR pattern (ADR-MONO-013 § D8.2): PR-A doc-only ACCEPTED recording; PR-B bootstrap artifact. PR-A may merge before PR-B.
- Churn-clock reset (ADR-MONO-003a § D2.1) occurs at PR-B `settings.gradle`/project-structure change — acknowledge in PR-B body + memory; if `console-web` is frontend-only with no Gradle module, the structural `projects/platform-console/` addition still counts as the new-project churn event.
- finance/erp scope guard: do not author their taxonomy/rules here.
- Branch name must not contain `master` substring (sandbox push regex).

---

# Edge Cases

- `console-web` is frontend-only → `settings.gradle` may not need a Gradle module; the new `projects/platform-console/` dir is still the churn event to record.
- GAP OIDC client seed collides with existing client IDs → namespace `platform-console` distinctly.
- `console.local` not in dev hosts → covered by existing `scripts/dev-setup` Traefik hostfile step (reuse, don't reinvent).

---

# Failure Scenarios

- ACCEPTED recorded but PR-B never lands → ADR claims ACCEPTED with no project (mitigated: single task tracks both PRs; § 6 ACCEPTED row notes "PR-B bootstrap artifact" pending until merged).
- Scope creep into Phase 2 (operator parity) → explicitly Out of Scope; PR-B = skeleton only.
- Trait bloat (declaring `internal-system`) → D3 explicitly rejects it; PROJECT.md must match D3 exactly.

---

# Test Requirements

- `console-web` lint + build (CI frontend job / path-filter).
- `./gradlew projects` lists the new project tree (if a module is added).
- ADR/spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] PR-A merged (ADR ACCEPTED + memory)
- [ ] PR-B merged (skeleton + GAP integration point + wiring + overview)
- [ ] Acceptance Criteria all satisfied
- [ ] Specs/contracts authored before any future implementation (Phase 2 prerequisite)
- [ ] Ready for review
