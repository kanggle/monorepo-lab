# platform-console — Per-Domain Ops Demo (on the federation-hardening-e2e stack)

> **Human reference only** (per `CLAUDE.md`, `docs/guides/` is NOT an AI source of truth).
> Authored by **TASK-MONO-170**.

Make **every** platform-console screen render live — including the four
per-domain ops pages (WMS · SCM · Finance · ERP 운영) that no existing stack
served — by adding a thin **additive overlay** to the already-containerized
`federation-hardening-e2e` harness.

| Screen | Proves | Active tenant |
|---|---|---|
| 운영자 통합 개요 / 도메인 상태 | BFF federation + health | any |
| GAP 운영 (계정·감사·운영자) | operator-token surface | always |
| **WMS 운영** | direct domain call, inventory read-model | **acme-corp** |
| **Finance 운영** | account / balances / transactions | **acme-corp** |
| **SCM 운영** | gateway-routed PO + inventory-visibility | **globex-corp** |
| **ERP 운영** | as-of masterdata reads | **globex-corp** |

## Why an overlay (not `pnpm *:up`)

The per-project `docker-compose.yml` files are **infrastructure only** — the
application services (GAP auth/account/admin, the 5 producers, console-bff,
console-web) run as containers ONLY via the `federation-hardening-e2e` harness
(or as host JVMs via `bootRun`). `pnpm gap:up` etc. start just DBs/redis/kafka.

The fed-e2e harness already runs the full app stack, but it was built for the
**BFF overview/health** legs + GAP, so it lacks two things the per-domain ops
pages need:

1. **The SCM gateway** — the SCM ops client calls `scm.local/api/v1/procurement/po`
   + `/api/v1/inventory-visibility/snapshot`; only the gateway maps those
   (`/api/v1/...` → `/api/...`) to the producers. The base runs the scm services
   directly, no gateway.
2. **console-web per-domain ops base URLs** — the base leaves
   `WMS_ADMIN_BASE_URL` / `SCM_GATEWAY_BASE_URL` / `FINANCE_BASE_URL` /
   `ERP_BASE_URL` unset, so they default to `*.local` (unreachable on the bridge
   network).

`docker-compose.federation-e2e.demo.yml` adds exactly these two (CI base compose
byte-unchanged). The per-domain ops clients use the **assumed / active-tenant
token** (ADR-MONO-020 D4 `getDomainFacingToken`); the scm gateway + producers
**dual-accept** the signed `entitled_domains` claim (ADR-MONO-019 §D5,
`TenantClaimValidator`), so a globex-corp token (`entitled_domains=[scm,erp]`)
passes.

## 1. Base harness must be UP first

The demo overlay sits on top of the running fed-e2e base. Bring the base up via
the harness (it builds + runs ~20 containers — see the CI workflow
`.github/workflows/federation-hardening-e2e*.yml` for the authoritative
build+seed sequence, or the compose header in
`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`).

> The base seeds: GAP operators (incl. **`multi-operator`**, N:M-assigned to
> acme-corp + globex-corp) + acme finance account + wms inventory + globex
> scm-inventory. GAP runs `SPRING_PROFILES_ACTIVE=e2e` so the globex-corp
> `[scm,erp]` subscription (account-service Flyway `migration-dev/V0021`) is
> present.

`console-demo:up` **detects** the base (checks `auth-service` is running) and
stops with guidance if it is absent.

## 2. Enable the demo

```bash
pnpm console-demo:up
```

(Windows-native:  `.\scripts\console-demo-up.ps1`)

This:
1. builds the scm gateway-service jar (`gradlew … gateway-service:bootJar`),
2. `docker compose -p federation-hardening-e2e -f <base> -f <demo> up -d --build scm-gateway console-web`
   (adds the gateway, recreates console-web with the 4 ops base URLs; restarts
   the gateway once if its JWKS startup-probe missed the recreate window),
3. seeds the **globex-corp delta** — SCM purchase-orders + ERP masters — so the
   globex ops pages render non-empty (the base already has globex scm-inventory).

Flags: `NO_BUILD=1` (skip jar+image build), `NO_SEED=1` (overlay only),
`HEALTH_TIMEOUT=180`. Re-seed only: `pnpm console-demo:seed`.

## 3. Walkthrough

Open **http://localhost:3000** → login:

```
multi-operator@example.com  /  devpassword123!
```

> **Use `multi-operator`.** super-admin (`*`) and acme-operator are NOT entitled
> to scm/erp — their SCM/ERP ops pages correctly show "테넌트 스코프가 부여되어
> 있지 않습니다" (the catalog-eligibility gate). That message means *wrong
> operator/tenant for that domain*, not a bug.

1. **Active tenant `acme-corp`** (`[finance,wms]`): 통합 개요 + 도메인 상태;
   **Finance 운영** (account balance) + **WMS 운영** (inventory snapshot); GAP 운영.
   SCM/ERP gate (not entitled — correct).
2. **Switch active tenant → `globex-corp`** (`[scm,erp]`): **SCM 운영** (PO
   `PO-DEMO-001` + inventory-visibility snapshot) + **ERP 운영** (department /
   cost-center / job-grade / employee). Finance/WMS now gate. **This A↔B flip is
   the live ADR-MONO-020 active-tenant scoping proof.**

Disable the overlay: `pnpm console-demo:down` (removes `scm-gateway`; the fed-e2e
base harness is untouched).

## 4. Seed bundle

`scripts/console-demo/seed/` — rows reuse the proven fed-e2e fixtures, tenant-
scoped for the demo. On the fed-e2e stack the base harness applies most; the
overlay script additionally applies the **globex delta**:

| File | fed-e2e target | applied by |
|---|---|---|
| `03-erp.sql` (globex masters) | `federation-hardening-e2e-mysql-1` `erp_db` | `console-demo:up` |
| `06-scm-procurement.sql` (globex PO) | `federation-hardening-e2e-scm-postgres-1` `scm_procurement` | `console-demo:up` |
| `01,02,04,05,07` | (base harness) | the fed-e2e seed sequence |

All idempotent (`INSERT IGNORE` / `ON CONFLICT DO NOTHING`).

## 5. Troubleshooting

- **`console-demo:up` says "base not running"** — bring up the fed-e2e harness
  first (§1). The overlay only adds the gateway + console env; it does not boot
  the base.
- **SCM/ERP ops "테넌트 스코프가 부여되어 있지 않습니다"** — you are not logged in
  as `multi-operator`, or the active tenant is acme-corp (not entitled to
  scm/erp). Switch to globex-corp / log in as multi-operator.
- **SCM ops "일시적으로 불러올 수 없습니다" (degraded)** — `scm-gateway` not
  healthy (`docker logs federation-hardening-e2e-scm-gateway-1`). The gateway
  has a 30s JWKS startup probe; if the base was mid-recreate it fail-fasts —
  re-run `pnpm console-demo:up` (the script restarts it once).
- **WMS ops alerts section degraded** — the WMS alerts read-model is NOT seeded
  (only inventory is). The inventory section is the WMS ops proof; alerts is a
  documented follow-up.
- **Host memory / OOM** — the base harness is ~20 containers + JVMs; the overlay
  adds one more JVM (the gateway). See `CLAUDE.md` → "Session Size / JDT.LS OOM
  Cascade" + project memory `env_jdtls_oom_cascade`.

## 6. What this is NOT

- Not a Traefik `*.local` dev environment (that requires `bootRun` host JVMs —
  far heavier; the fed-e2e bridge net + container DNS is used instead).
- Not a CI job — the fed-e2e harness remains the CI federation gate; this overlay
  is local-demo-only and leaves the CI base compose byte-unchanged.
- Not a single-tenant all-5 view (the `multi-operator` 2-tenant switch covers all
  domains; a single all-5-entitled tenant would need a new GAP Flyway migration).
