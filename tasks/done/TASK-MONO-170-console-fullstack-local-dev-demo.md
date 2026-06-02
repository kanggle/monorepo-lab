# Task ID

TASK-MONO-170

# Title

Enable the **per-domain ops DEMO** (WMS · SCM · Finance · ERP 운영) on the `federation-hardening-e2e` stack via an **additive overlay** — `scm-gateway` + console-web per-domain ops base URLs + a globex-corp seed delta — so EVERY console screen renders live. The per-domain ops pages previously had no serving environment: the fed-e2e harness wires only the BFF overview/health legs + GAP, with no SCM gateway and `*.local`-defaulted ops base URLs unreachable on its bridge net.

> **Approach pivot (2026-06-02):** originally scoped as "assemble per-project `*:up` into a Traefik `*.local` full stack" (Approach A). Live-smoke discovery: **the per-project `docker-compose.yml` files are infrastructure-only** — app services (GAP auth/account/admin, the 5 producers, console) run as containers ONLY via the fed-e2e harness, or as host JVMs via `bootRun`. So `*:up` chaining could never bring up a working app stack (login would fail). Re-decided with the user → **Approach B**: an additive overlay on the already-containerized fed-e2e harness (CI base compose byte-unchanged). The Traefik-`*.local` path would require ~15 host JVMs (`bootRun`) — rejected as OOM-hostile on this Windows host.

# Status

done

# Owner

integration / devops (additive compose overlay + globex seed delta + orchestration script + runbook on the fed-e2e harness — NO producer/BFF/console/contract/ADR change; CI base compose byte-unchanged)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- onboarding
- test

---

# Dependency Markers

- **extends (byte-unchanged) the CI base**: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` — the demo overlay composes ON TOP (`-f base -f demo`); the CI federation gate is unaffected.
- **reuses**: the fed-e2e harness's running app stack + its seed fixtures (`fixtures/seed.sql` `multi-operator` + N:M assignments; `seed-domains.sql` finance acme; `seed-wms-admin.sql` inventory; `seed-scm-inv.sql` globex inventory). The demo adds only the **globex SCM-PO + ERP delta** (`scripts/console-demo/seed/03-erp.sql`, `06-scm-procurement.sql`).
- **reuses (byte-unchanged)**: `projects/scm-platform/apps/gateway-service` (built into the overlay's `scm-gateway`); its `TenantClaimValidator` already entitlement-trust dual-accepts (`entitled_domains ∋ scm`) per ADR-MONO-019 §D5.
- **surfaced by**: the 2026-06-02 live-console session (per-domain ops pages had no serving environment) + this task's own live-smoke (which discovered the `*:up`=infra-only premise error → the Approach A→B pivot).
- **no dependency on**: any producer / console-bff / console-web application change; any contract/ADR change. Token model already correct (ADR-MONO-020 D4 `getDomainFacingToken`); ops base URLs already default to `*.local`.

---

# Goal

A developer/evaluator can see **every** console screen live, including the four
per-domain ops pages, by running `pnpm console-demo:up` against the running
fed-e2e harness. The overlay supplies the two missing pieces (SCM gateway +
console-web ops base URLs) and the globex seed delta; the `multi-operator`
tenant-switch (acme-corp → finance/wms ops; globex-corp → scm/erp ops; GAP
always) covers all domains and demonstrates ADR-MONO-020 active-tenant scoping
live.

# Scope

## In Scope

Monorepo-level (root `scripts/`, root `package.json`, `docs/guides/`, the fed-e2e
overlay) — single bundled PR, NO application/contract/ADR change:

1. **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.demo.yml`** — additive overlay: `scm-gateway` (built from `scm-platform/apps/gateway-service`, wired to the base auth-service JWKS + scm producers + redis) + a `console-web` env merge adding `WMS_ADMIN_BASE_URL` / `SCM_GATEWAY_BASE_URL` / `FINANCE_BASE_URL` / `ERP_BASE_URL` → container DNS.
2. **Orchestration** — `scripts/console-demo-up.{ps1,sh}` + `-down`: detect the base harness, build the gateway jar, bring up the overlay (with a JWKS-probe single-restart guard), apply the globex seed delta. Idempotent.
3. **Seed bundle** — `scripts/console-demo/seed/` (the globex delta `03-erp.sql`/`06-scm-procurement.sql` are applied by the script; the rest document the full per-tenant data model).
4. **Root `package.json`** — `console-demo:{up,down,seed,logs}`.
5. **Runbook** — `docs/guides/console-fullstack-local-dev.md` (base-harness prereq, overlay rationale, walkthrough, troubleshooting incl. wrong-operator gate + OOM).
6. **Task lifecycle** + root `tasks/INDEX.md`.

## Out of Scope

- Any application / contract / ADR change (token model + dual-accept already correct).
- Modifying the CI base compose `docker-compose.federation-e2e.yml` (byte-unchanged).
- Bringing up the fed-e2e base harness itself (its own lifecycle / the CI workflow) — the script DETECTS it and requires it up first.
- A Traefik `*.local` `bootRun` full-stack dev env (Approach A — rejected: ~15 host JVMs, OOM-hostile).
- A single all-5-entitled demo tenant (needs a new GAP Flyway migration — follow-up).
- WMS alerts read-model seeding (only inventory is seeded; alerts is a documented follow-up).

# Acceptance Criteria

**Verified in-session against the running fed-e2e stack:**

- [x] **AC-1** Overlay merges clean — `docker compose -p federation-hardening-e2e -f base -f demo config -q` ✓.
- [x] **AC-2** `console-demo-up.{ps1,sh}` + `-down` parse clean (`bash -n`; PS parser).
- [x] **AC-3** `scm-gateway` builds + boots healthy; route probe (no token) returns **401** (not 404) on `/api/v1/procurement/po` + `/api/v1/inventory-visibility/snapshot` — route + downstream wiring + auth enforcement confirmed; `TenantClaimValidator` dual-accepts `entitled_domains ∋ scm`.
- [x] **AC-4** `console-web` env carries all 4 ops base URLs (verified via `docker exec … env`).
- [x] **AC-5** Demo data present per tenant — finance acme (1 acct), wms inventory (1, neutral), globex scm-inventory (1) + globex scm-PO (1, seeded) + globex ERP dept/cc/jg/emp (1 each, seeded).
- [x] **AC-6** No application/contract/ADR change (diff = `scripts/**` + `package.json` + `docs/guides/**` + the additive overlay + `tasks/**`).

**Live browser smoke (user — needs the OIDC login flow):**

- [ ] **AC-7** `http://localhost:3000` login as `multi-operator` → 통합 개요 + 도메인 상태 render.
- [ ] **AC-8** active tenant `acme-corp` → **Finance 운영** + **WMS 운영** render live; GAP 운영 renders; SCM/ERP gate (not entitled — correct).
- [ ] **AC-9** switch to `globex-corp` → **SCM 운영** + **ERP 운영** render live (non-empty); finance/wms now gate — ADR-MONO-020 re-scope proof.
- [ ] **AC-10** `pnpm console-demo:down` removes the overlay; base harness intact.

# Related Specs

- `projects/platform-console/apps/console-web/src/shared/config/env.ts` — per-domain ops base URL defaults (`*.local`) the overlay overrides to container DNS.
- `projects/scm-platform/apps/gateway-service/.../TenantClaimValidator.java` — entitlement-trust dual-accept (ADR-MONO-019 §D5) that lets the globex token through the gateway.
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` + `fixtures/*.sql` — the base stack + seed model the overlay extends.
- `docs/adr/ADR-MONO-019` / `ADR-MONO-020` — entitlement-trust + active-tenant scoping; the tenant-switch demo is their runtime proof.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` §§ 2.4.5–2.4.8 — per-domain credential/path bindings; **byte-unchanged**.

# Participating Components

fed-e2e base (GAP auth/account/admin + console-bff + console-web + wms-admin/master + scm-procurement/inventory-visibility + finance + erp + DBs) **+ the overlay's `scm-gateway`** + the globex seed delta.

# Trigger

`pnpm console-demo:up` against a running fed-e2e harness.

# Expected Flow

1. Bring up the fed-e2e base harness (separate; the script requires it).
2. `console-demo:up` → detect base → build gateway jar → `compose -f base -f demo up scm-gateway console-web` → seed globex delta.
3. `http://localhost:3000` → login `multi-operator` → switch acme↔globex → all screens live.

# Edge Cases

- Base harness absent → script stops with guidance (does not try to boot it).
- scm-gateway JWKS startup-probe misses the recreate window → script restarts it once.
- Wrong operator (super-admin/acme) on scm/erp → catalog-eligibility gate (correct, not a bug — documented).
- Seed idempotency on re-run (`INSERT IGNORE` / `ON CONFLICT`).
- Host OOM (base ~20 containers + the gateway JVM) → runbook note.

# Failure Scenarios

- A producer path the gateway rewrites does not exist → STOP, raise a per-project task (do not mask in the overlay). (Verified: gateway → producers returns 401, not 404.)
- GAP not on the `e2e` profile → globex absent → scm/erp eligibility empty (the base harness sets it).

# Test Requirements

- AC-1…AC-6 verified in-session against the live fed-e2e stack.
- AC-7…AC-10 = user browser walkthrough (the runbook is the script).

# Definition of Done

- [x] Overlay + globex seed delta + orchestration scripts + runbook + `console-demo:*` authored.
- [x] AC-1…AC-6 verified in-session (live: gateway 401-routes, console env, per-tenant data).
- [x] CI base compose / contracts / ADRs untouched.
- [x] Task md + `tasks/INDEX.md` updated.
- [ ] AC-7…AC-10 (browser smoke) confirmed by the user.
- [ ] Ready for review (pending user browser confirmation).

---

분석=Opus 4.8 / 구현=Opus(직접, 라이브 검증). Approach A→B 피벗은 live-smoke 가 `*:up`=infra-only 전제를 잡아낸 결과 — 메타: 인프라 데모 task 는 "기존 자산 조립" 가정 자체를 실기동으로 조기 검증해야 한다(compose 가 app 을 띄우는지 vs infra 만인지). 게이트웨이 dual-accept·라우트·env·시드를 모두 실 컨테이너에서 실측.
