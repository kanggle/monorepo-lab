# platform-console — Full-Stack Local-Dev Demo

> **Human reference only** (per `CLAUDE.md`, `docs/guides/` is NOT an AI source of truth).
> Authored by **TASK-MONO-170**.

Bring up the **entire** platform-console portfolio demo locally — all 5 backend
domains (GAP · WMS · SCM · Finance · ERP) + console-bff + console-web — behind
the shared Traefik `*.local` router, with demo data + a demo operator, so EVERY
console screen renders live:

| Screen | What it proves | Demo path |
|---|---|---|
| 운영자 통합 개요 | 5-domain BFF federation | any tenant |
| 도메인 상태 | per-domain health composition | any tenant |
| GAP 운영 (계정 · 감사 · 운영자) | operator-token surface | always |
| **WMS 운영** | direct domain call, inventory read-model | **active tenant = acme-corp** |
| **Finance 운영** | account/balances/transactions | **active tenant = acme-corp** |
| **SCM 운영** | gateway-routed PO + inventory-visibility | **active tenant = globex-corp** |
| **ERP 운영** | as-of masterdata reads | **active tenant = globex-corp** |

The per-domain ops pages call the domains **directly** with the active-tenant
**assumed** token (ADR-MONO-020 D4 `getDomainFacingToken()`). Their base URLs
default to `*.local` (`shared/config/env.ts`), so with Traefik + the per-project
composes up they work with **no console override**.

---

## 1. Prerequisites (one-time)

1. **Docker** running (Rancher Desktop or Docker Desktop).
2. **`*.local` hosts entries** — run as **Administrator**:
   ```powershell
   .\scripts\dev-setup.ps1        # Windows
   ./scripts/dev-setup.sh         # macOS/Linux (sudo)
   ```
   Registers `console.local`, `gap.local`, `wms.local`, `scm.local`,
   `finance.local`, `erp.local` (+ others) → `127.0.0.1`.
3. **Git Bash** on PATH (Windows) — the `pnpm console-demo:*` scripts invoke
   `bash`. On Windows you may instead run the native PowerShell scripts directly
   (see below).

---

## 2. One command

```bash
pnpm console-demo:up
```

(Windows-native alternative — identical behaviour:)
```powershell
.\scripts\console-demo-up.ps1
```

This runs, in order, **health-gating between phases**:

1. `traefik:up` (creates/joins `traefik-net`).
2. **GAP** with `SPRING_PROFILES_ACTIVE=e2e` → wait healthy → seed operators.
   - The `e2e` profile loads `db/migration-dev/V0021` (globex-corp `[scm,erp]`)
     **in addition to** the always-loaded `db/migration/V0020` (acme-corp
     `[finance,wms]`). Both demo customers therefore exist with their
     `entitled_domains` subscriptions. The datasource is env-driven, so the
     per-project GAP compose DB wiring is unaffected.
3. **wms / scm / finance / erp** → wait healthy → seed per-domain read-models.
4. **console** (console-bff + console-web) → wait healthy.

Flags:

| Flag (sh env / ps1 switch) | Effect |
|---|---|
| `NO_BUILD=1` / `-NoBuild` | skip image (re)build (faster re-runs) |
| `NO_SEED=1` / `-NoSeed` | bring up services only |
| `HEALTH_TIMEOUT=240` / `-HealthTimeoutSec 240` | per-phase health-wait timeout |

Re-seed only (idempotent): `pnpm console-demo:seed`.

---

## 3. Demo walkthrough

Open **http://console.local** → login:

```
multi-operator@example.com  /  devpassword123!
```

`multi-operator` is N:M-assigned to **both** customer tenants. The tenant
switcher (top bar) lists `acme-corp` and `globex-corp`.

1. **Active tenant `acme-corp`** (`entitled_domains=[finance,wms]`):
   - 통합 개요 + 도메인 상태 render.
   - **Finance 운영** → account `…a200` balance 5,000.00 KRW.
   - **WMS 운영** → inventory snapshot row (`DEMO-LOC-01` / `DEMO-SKU-01`, 100 on-hand).
   - GAP 운영 → accounts / audit / operators.
   - SCM 운영 / ERP 운영 → **gated** ("이 화면을 조회할 권한이 없습니다") — acme-corp is not entitled to scm/erp. This is correct (entitlement-trust).
2. **Switch active tenant → `globex-corp`** (`entitled_domains=[scm,erp]`):
   - **SCM 운영** → PO `PO-DEMO-001` + inventory-visibility snapshot (`SKU-DEMO-001`, qty 42).
   - **ERP 운영** → department/cost-center/job-grade/employee masters.
   - Finance / WMS now gate — the active-tenant re-scope flipped the
     entitlement. **This A↔B flip IS the live ADR-MONO-020 proof.**

> A single operator+tenant entitled to all 5 at once is intentionally **not**
> seeded (it needs a new GAP Flyway migration — a follow-up). The 2-tenant
> switch covers all four non-GAP domains AND showcases active-tenant scoping.

Tear down: `pnpm console-demo:down` (add `VOLUMES=1` / `-Volumes` for a full
data reset).

---

## 4. Seed manifest

`scripts/console-demo/seed/` (row content reuses the proven
`tests/federation-hardening-e2e/fixtures/*.sql`, re-targeted per-project):

| File | Container | DB | User |
|---|---|---|---|
| `01-gap.sql` | `gap-mysql` | auth_db + admin_db | root / `rootpass` |
| `02-finance.sql` | `finance-platform-mysql` | finance_db | root / `root` |
| `03-erp.sql` | `erp-platform-mysql` | erp_db | root / `root` |
| `04-wms-master.sql` | `wms-postgres` | master_db | postgres / `postgres` |
| `05-wms-admin.sql` | `wms-postgres` | admin_db | postgres / `postgres` |
| `06-scm-procurement.sql` | `scm-platform-postgres` | scm_procurement | scm / `scm` |
| `07-scm-inventory.sql` | `scm-platform-postgres` | scm_inventory_visibility | scm / `scm` |

Customers/entitlements (`acme-corp`/`globex-corp` subscriptions) come from GAP
account-service Flyway, NOT these files. All seeds are idempotent.

---

## 5. Troubleshooting

### ⚠ Host memory / OOM cascade (Windows)

This stack is **~25 containers including 5+ JVM services**. On this Windows host
that can approach the Windows commit limit and trigger the documented JDT.LS /
`errno=1455` OOM cascade (see `CLAUDE.md` → "Session Size / JDT.LS OOM Cascade"
and project memory `env_jdtls_oom_cascade`). If the host is memory-constrained:

- **Bring up a SUBSET.** GAP + one domain + console is enough to demo that
  domain. e.g. for the Finance demo:
  ```bash
  pnpm traefik:up
  SPRING_PROFILES_ACTIVE=e2e pnpm gap:up        # wait healthy, then seed 01-gap.sql
  pnpm finance:up                                # wait healthy, then seed 02-finance.sql
  pnpm console:up
  ```
- Close other JVM-heavy apps (IDE language servers) before bring-up.
- Prefer Rancher Desktop with an adequate memory allocation.

### A per-domain ops page shows "권한이 없습니다" (403)

The active tenant does not entitle that domain. Switch tenants (acme→finance/wms,
globex→scm/erp). If a domain gates for the *entitled* tenant, the GAP `e2e`
profile may not have loaded (globex needs it) — confirm GAP came up with
`SPRING_PROFILES_ACTIVE=e2e`.

### A per-domain ops page shows "일시적으로 불러올 수 없습니다" (degraded)

The domain service/gateway is not reachable or the read-model is unseeded.
- Confirm the domain is healthy: `pnpm <domain>:ps`.
- Confirm Traefik routes the hostname: `curl -H 'Host: scm.local' http://localhost/...`.
- Re-apply seeds: `pnpm console-demo:seed`.
- **WMS 운영 alerts section** specifically may stay degraded — the WMS alerts
  read-model is **not** seeded by this demo (only inventory is). The inventory
  section is the WMS ops proof; seeding alerts is a follow-up.

### Stale console image (missing recent FE work)

Rebuild: `pnpm console-demo:up` (default `--build`) or
`docker compose --project-directory projects/platform-console up -d --build`.

### SCM 운영 404 on `/api/v1/procurement/po`

The console targets the **scm gateway** (`scm.local`), which maps
`/api/v1/procurement/**` and `/api/v1/inventory-visibility/**` to the services.
Confirm `scm-platform-gateway` is up and routed (not just the services).

---

## 6. What this demo is NOT

- Not a CI job — the `federation-hardening-e2e` harness remains the CI federation
  gate. This is a local-dev demo.
- Not a single-tenant all-5 view (follow-up — needs a GAP demo-tenant migration).
- Not fan-platform / ecommerce (outside the 5-domain console federation).
