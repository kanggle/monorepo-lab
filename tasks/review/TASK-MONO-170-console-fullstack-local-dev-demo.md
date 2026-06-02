# Task ID

TASK-MONO-170

# Title

Author a one-command **full 5-domain platform-console local-dev demo environment** — orchestration script + cross-domain demo seed + runbook — so EVERY console screen (운영자 통합 개요 · 도메인 상태 · GAP 운영 · **WMS / SCM / Finance / ERP per-domain 운영**) renders live against real producers over Traefik `*.local` hostname routing. The per-domain ops pages currently cannot be demoed in any existing stack (the federation-hardening-e2e harness wires only the BFF overview/health legs + GAP, on a bare bridge network with no `*.local` and no SCM gateway). This task assembles the EXISTING per-project composes / `*:up` scripts / fed-e2e seed fixtures into a single ordered bring-up; it does NOT author new producer composes (they already join `traefik-net` and register `*.local`).

# Status

review

# Owner

integration / devops (orchestration script + cross-domain seed + runbook; reuses existing per-project docker-compose.yml + root `*:up` scripts + fed-e2e seed fixtures — NO producer/BFF/console application change, NO contract/ADR change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- onboarding
- test

---

# Dependency Markers

- **reuses (does NOT modify)**: each project's `projects/<name>/docker-compose.yml` (gap/wms/scm/finance/erp/console) — all ALREADY join the external `traefik-net` and register their `*.local` hostname via Traefik labels (gap.local / wms.local / scm.local; finance.local → account-service direct, erp.local → masterdata-service direct — gateways v1-deferred for those two). Root `package.json` ALREADY has `traefik:up`, `gap:up`, `wms:up`, `scm:up`, `finance:up`, `erp:up`, `console:up`. `scripts/dev-setup.ps1`/`.sh` ALREADY registers the 9 `*.local` hosts entries (incl. console.local, console-bff.local). **This task wires them together — it does not re-author them.**
- **reuses (re-targets, does NOT modify in place)**: `tests/federation-hardening-e2e/fixtures/seed.sql` (GAP operators/credentials + `multi-operator` D1 assignments), `seed-domains.sql` (finance/erp read-model), `seed-wms.sql` / `seed-wms-admin.sql` (wms warehouses + admin inventory snapshot), `seed-scm.sql` / `seed-scm-inv.sql` (scm PO + inventory-visibility). The SQL *content* is reusable; the *target* differs (fed-e2e DB containers vs each project's own DB container/port). The demo seed bundle adapts the apply-target, not the rows.
- **surfaced by**: the 2026-06-02 live console-verification session (after PC-FE-035/036/037). 운영자 통합 개요 + 도메인 상태 + GAP 운영 worked in the fed-e2e stack; WMS/SCM/Finance/ERP 운영 did NOT — diagnosed as purely environmental: (a) console-web per-domain ops base URLs unset in fed-e2e (a temp override reached only WMS inventory), (b) fed-e2e has no SCM **gateway** (the console calls `scm.local/api/v1/procurement/po` + `/api/v1/inventory-visibility/snapshot`, which only the gateway maps — the direct services serve `/api/scm/purchase-orders` + `/api/inventory-visibility/snapshot`), (c) per-domain read-model under-seeded (WMS inventory 200 but alerts 500-unseeded). NONE is a code defect — the token model is already correct (ADR-MONO-020 D4: `getDomainFacingToken()` uses the assumed/active-tenant token, verified in wms/scm/finance/erp-api.ts).
- **no dependency on**: any producer / console-bff / console-web application change; any contract or ADR change. `console-integration-contract` §§ 2.4.5–2.4.8 (per-domain credential + path bindings) are the AUTHORITY this task satisfies, byte-unchanged.

---

# Goal

Make the WHOLE platform-console demoable locally with a single command. Today an evaluator can see the federation screens (overview/health) + GAP ops via the CI fed-e2e harness, but the four per-domain ops surfaces (WMS/SCM/Finance/ERP 운영) have NO environment that serves them — they require:

1. **Traefik `*.local` hostname routing** — the console's per-domain ops clients default to `http://wms.local/api/v1/admin`, `http://scm.local`, `http://finance.local`, `http://erp.local` (`shared/config/env.ts`). With Traefik + the per-project composes up, these defaults work with NO console override (unlike the fed-e2e temp override).
2. **The SCM gateway** — `scm-platform/docker-compose.yml`'s `gateway-service` (scm.local) maps `/api/v1/procurement/**` → procurement-service and `/api/v1/inventory-visibility/**` → inventory-visibility-service. The console targets the gateway, not the services.
3. **An entitled operator + active-tenant** — per ADR-MONO-019/020 entitlement-trust, each domain synthesizes its VIEWER role from the active tenant's `entitled_domains`. The demo operator must be entitled across the domains being shown.
4. **Per-domain read-model demo data** — non-empty inventory/PO/account/masterdata rows so each ops page renders content, not an empty state.

Deliver an ordered orchestration (traefik → GAP → 4 domains → console), a re-runnable cross-domain demo seed, a runbook, and `console-demo:*` root scripts. The demo narrative reuses the existing `multi-operator` (home `acme-corp`, D1-assigned to BOTH `acme-corp` [finance,wms] AND `globex-corp` [scm,erp]): logging in and switching the active tenant A↔B covers all four non-GAP domains AND showcases the ADR-MONO-020 active-tenant re-scoping live (GAP ops is always available via the operator-token). A single all-5-entitled demo tenant is an explicit OUT-OF-SCOPE follow-up (it needs a new GAP Flyway dev migration).

# Scope

## In Scope

Monorepo-level (root `scripts/`, root `package.json`, `docs/guides/`, a new seed-bundle dir) — single bundled PR, NO application/contract/ADR change:

1. **Orchestration scripts** — `scripts/console-demo-up.ps1` + `scripts/console-demo-up.sh` (+ matching `-down`):
   - Ordered bring-up: `traefik:up` → `gap:up` (wait GAP healthy) → apply GAP demo seed → `wms:up` / `scm:up` / `finance:up` / `erp:up` (wait healthy) → apply per-domain read-model seed → `console:up` (wait healthy).
   - Health-gating between phases (poll `actuator/health` / `api/health`), bounded retries, clear progress + failure output.
   - Idempotent / re-runnable (safe to re-invoke; seeds use `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` / `IF NOT EXISTS`).
   - A preflight check that the `*.local` hosts entries + `traefik-net` exist (point at `scripts/dev-setup.ps1` + `pnpm traefik:up` if not).
2. **Cross-domain demo seed bundle** — a directory (e.g. `scripts/console-demo/seed/`) adapting the fed-e2e fixtures to apply against EACH project's own DB container (resolve container name + db/user per project compose). GAP operators/credentials + `multi-operator` assignments + per-domain read-model rows. Document the dev-seed Flyway profile requirement (acme/globex `entitled_domains` subscriptions come from GAP account-service Flyway `migration-dev` V0019/V0020/V0021 — the orchestration MUST bring GAP up with the profile that loads them, else entitlement-trust returns empty → ops 403).
3. **Root `package.json` scripts** — `console-demo:up`, `console-demo:down`, `console-demo:seed`, `console-demo:logs` delegating to the scripts above.
4. **Runbook** — `docs/guides/console-fullstack-local-dev.md` (human-only reference per CLAUDE.md): prerequisites (Docker, hosts file, Traefik), the one-command flow, the demo login (`multi-operator@example.com` / `devpassword123!`) + tenant-switch walkthrough (acme → finance/wms ops; globex → scm/erp ops; GAP always), per-screen expected result, and a troubleshooting section INCLUDING the documented Windows JDT.LS / commit-limit OOM-cascade + Rancher npipe risk of a ~25-container stack (link the project memory / CLAUDE.md note) and a "bring up a subset" fallback.
5. **Task lifecycle** + root `tasks/INDEX.md` entry.

## Out of Scope

- **Any producer / console-bff / console-web application code change.** Base URLs already default to `*.local`; the token model (ADR-MONO-020 D4 domain-facing token) is already correct. If a screen needs a code change to render, that is a SEPARATE task (this task only assembles environment + seed).
- **Modifying any per-project `docker-compose.yml`** in place. If a producer compose genuinely cannot serve its console path under Traefik (e.g. a missing gateway route), STOP and raise a per-project task — do not patch the shared orchestration to mask it.
- **A single all-5-domain-entitled demo tenant** (one login, no tenant-switch, all 4 non-GAP ops at once). Requires a new GAP account-service Flyway `migration-dev` migration (new tenant + 5 subscriptions + keystone reverse-lookup) + admin operator/credential — a cross-project follow-up enhancement, not this task. The `multi-operator` 2-tenant-switch demo covers all domains without new GAP code.
- **CI integration.** This is a local-dev demo environment, not a CI job. The fed-e2e harness remains the CI federation gate. (A future task could add a per-domain-ops CI leg, but that belongs with the fed-e2e cohort.)
- **fan-platform / ecommerce.** Out of the platform-console federation scope (5 backend domains = gap/wms/scm/finance/erp).

# Acceptance Criteria

**Authored-artifact AC (verifiable in-session by the implementer — no full stack bring-up):**

- [ ] **AC-1** `docker compose config` validates clean for every compose the orchestration references (traefik + gap + wms + scm + finance + erp + console), via the existing `*:docker config` / `docker compose --project-directory … config` paths.
- [ ] **AC-2** `console-demo-up.ps1` + `.sh` parse without syntax error (`pwsh -NoProfile -Command "& { … }"` parse / `bash -n`) and the phase ordering + health-gating + idempotency are present and reviewable.
- [ ] **AC-3** Every per-domain base URL + path the orchestration relies on is cross-checked against the producer spec and the console client: WMS `wms.local/api/v1/admin/dashboard/*`, SCM `scm.local/api/v1/{procurement,inventory-visibility}/*` (gateway-mapped), Finance `finance.local/api/finance/accounts/*`, ERP `erp.local/api/erp/masterdata/*`. Documented in the task/runbook with the source line.
- [ ] **AC-4** The seed bundle's apply-target (container name + db + user) is correct for each project compose, and the GAP dev-seed Flyway profile requirement (acme/globex `entitled_domains`) is documented + wired into the up-script.
- [ ] **AC-5** Root `package.json` gains `console-demo:{up,down,seed,logs}`; the runbook `docs/guides/console-fullstack-local-dev.md` exists and is complete (prereqs, flow, demo login + tenant-switch walkthrough, per-screen expectation, OOM/host-risk troubleshooting).
- [ ] **AC-6** No application/contract/ADR file changed (diff is `scripts/**` + root `package.json` + `docs/guides/**` + `tasks/**` only). `console-integration-contract` §§ 2.4.5–2.4.8 unchanged.

**Live-bringup smoke AC (run by the USER on their machine — a ~25-container JVM stack; NOT in-session due to the documented Windows OOM-cascade / Rancher npipe host risk):**

- [ ] **AC-7** `pnpm console-demo:up` brings the full stack to healthy and `http://console.local` serves the login.
- [ ] **AC-8** Logged in as `multi-operator`, with active tenant `acme-corp`: 통합 개요 + 도메인 상태 render; **Finance 운영** + **WMS 운영** render live content (non-empty). GAP 운영 (계정/감사/운영자) renders.
- [ ] **AC-9** Switching the active tenant to `globex-corp`: **SCM 운영** + **ERP 운영** render live content; finance/wms now gate (entitlement re-scoped) — demonstrating ADR-MONO-020 active-tenant scoping end-to-end.
- [ ] **AC-10** `pnpm console-demo:down` tears the stack down cleanly.

# Related Specs

> Step 0 per `platform/entrypoint.md`: this is a monorepo-level infra/onboarding task. Target project for the federation surface = `projects/platform-console/PROJECT.md` (domain=platform-console-equiv; the console federates the 5 backend domains). No domain/trait rule conflict — this task adds no application logic.

- `projects/platform-console/apps/console-web/src/shared/config/env.ts` — the per-domain base URL defaults (`WMS_ADMIN_BASE_URL`/`SCM_GATEWAY_BASE_URL`/`FINANCE_BASE_URL`/`ERP_BASE_URL` → `*.local`). The environment this task builds is precisely what makes these defaults resolve.
- `projects/platform-console/docker-compose.yml` — Model B console compose (console-web + console-bff on traefik-net; header documents "domains run from their own compose files").
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` + `fixtures/*.sql` — the proven cross-product service wiring + seed rows this task re-targets from the bare bridge network to Traefik `*.local`.
- `docs/adr/ADR-MONO-001-port-prefix-scaling.md` — the Local Network Convention (Traefik hostname routing) this demo runs on.
- `docs/adr/ADR-MONO-019` / `ADR-MONO-020` — customer-tenant entitlement-trust + operator N:M active-tenant scoping; the demo's tenant-switch narrative IS the runtime proof of these.

# Related Skills

- `.claude/skills/INDEX.md` — consult for any devops/compose/onboarding skill guidance.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` §§ 2.4.5 (wms) / 2.4.6 (scm) / 2.4.7 (finance) / 2.4.8 (erp) — the per-domain credential + path + tenant bindings. **Byte-unchanged**; this task provides the environment in which the already-implemented bindings reach live producers.

# Participating Components

- Traefik (`infra/traefik/docker-compose.yml`) — `*.local` hostname router.
- GAP (`global-account-platform`) — auth-service (OIDC AS, gap.local) + account-service (registry/keystone) + admin-service (operator-token exchange + registry) + gateway-service.
- WMS (`wms-platform`) — gateway-service (wms.local) + admin-service (`/api/v1/admin/dashboard/*` inventory+alerts) + master-service + its postgres sidecars.
- SCM (`scm-platform`) — gateway-service (scm.local) + procurement-service + inventory-visibility-service + postgres sidecars.
- Finance (`finance-platform`) — account-service (finance.local direct; gateway v1-deferred).
- ERP (`erp-platform`) — masterdata-service (erp.local direct; gateway v1-deferred).
- platform-console — console-bff + console-web (console.local).
- Cross-domain demo seed bundle (new, `scripts/console-demo/seed/`).

# Trigger

A developer/evaluator runs `pnpm console-demo:up` to bring up the full portfolio console demo locally.

# Expected Flow

1. **Preflight** — verify `traefik-net` + `*.local` hosts entries (else point at `dev-setup` + `traefik:up`).
2. `traefik:up`.
3. `gap:up` with the **dev-seed Flyway profile** → wait auth/account/admin healthy → apply GAP demo seed (operators/credentials/`multi-operator` assignments; acme/globex subscriptions come from GAP Flyway migration-dev).
4. `wms:up` / `scm:up` / `finance:up` / `erp:up` → wait healthy → apply per-domain read-model seed (each against its own DB container).
5. `console:up` → wait healthy.
6. Open `http://console.local` → login `multi-operator@example.com` / `devpassword123!` → switch tenant acme↔globex → all screens live.

# Edge Cases

- **Host OOM-cascade risk** (CLAUDE.md / project memory `env_jdtls_oom_cascade`): ~25 containers + 5+ JVMs on Windows can exhaust the commit limit. The runbook MUST document this + a subset-bring-up fallback (e.g. GAP + one domain + console).
- **Phase health races** — a domain coming up before GAP JWKS is reachable → bring-up must health-gate GAP before domains, domains before console.
- **Seed idempotency** — re-running `console-demo:up` must not duplicate rows or fail on existing data.
- **dev-seed profile absent** — if GAP boots with the production profile, acme/globex subscriptions are absent → entitlement-trust empty → every non-GAP ops page 403. The up-script must set the profile.
- **Stale console image** — console-web image must be rebuilt from current main (a stale image silently omits PC-FE-033/034/036/037). Document `--build`.
- **hosts file not Administrator-editable** — `dev-setup.ps1` needs elevation; the preflight must detect + instruct, not silently proceed.

# Failure Scenarios

- **A producer compose cannot serve its console path under Traefik** (e.g. a missing/!mapped gateway route) → STOP, raise a per-project task; do NOT patch the orchestration to mask a producer gap.
- **SCM gateway route mismatch** — if `scm.local/api/v1/procurement/po` 404s, the gateway route map is the producer's responsibility (scm-platform task), not this orchestration.
- **Token rejected by a domain (401/403)** despite an entitled tenant → a GAP issuer/JWKS misconfig in the per-project compose env (verify `OIDC_ISSUER_URL`/`JWT_JWKS_URI` resolve to gap.local across all composes).
- **Down-script leaves orphans** — `console-demo:down` must tear down all 7 compose projects (+ optionally prune the demo seed volumes), not just console.

# Test Requirements

- Authored-artifact verification (AC-1…AC-6): compose-config validation, script parse, path/port cross-check, diff-scope check — all in-session.
- Live smoke (AC-7…AC-10): the user runs the stack on their machine and walks the tenant-switch demo. The runbook is the test script.

# Definition of Done

- [ ] Orchestration scripts + seed bundle + runbook + `console-demo:*` scripts authored.
- [ ] AC-1…AC-6 verified in-session (compose config, script parse, path cross-check, diff scope).
- [ ] Contracts/ADRs untouched (verified).
- [ ] Task md + `tasks/INDEX.md` updated.
- [ ] Pushed; AC-7…AC-10 (live smoke) handed to the user as the runbook walkthrough.
- [ ] Ready for review.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 — 이 task 는 **기존 자산 조립**(per-project compose + `*:up` 스크립트 + fed-e2e 시드 fixtures)이지 새 도메인 로직 0. 오케스트레이션 스크립트 + 시드 re-target + 런북 작성 + compose-config/path 교차검증이 본체. 단 라이브 스모크(AC-7~10)는 ~25-컨테이너 JVM 스택이라 문서화된 Windows OOM-cascade 위험상 사용자 머신 단계 — 산출물은 구현이 끝까지 작성·정적검증, 라이브 기동은 런북으로 인계.
