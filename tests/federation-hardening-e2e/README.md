# Phase 8 Federation Hardening E2E Suite

**TASK-MONO-139** — ADR-MONO-018 D1/D2/D3 cross-product e2e cohort (MVP).

## Overview

Cross-product Playwright suite that exercises all 5 backend domains (GAP + wms + scm + finance + erp) through the platform-console (console-web + console-bff) operator surface. Root-scoped (no single project owner; see ADR-MONO-018 D1).

**7 spec files (ADR-018 D3 MVP):**

| Spec | Domain | Assertion |
|---|---|---|
| `iam-golden-path.spec.ts` | GAP | operator registry list 200 + 1+ row |
| `wms-golden-path.spec.ts` | wms | warehouse list 200 + seed row |
| `scm-golden-path.spec.ts` | scm | purchase order list 200 + seed PO |
| `finance-golden-path.spec.ts` | finance | account detail 200 + KRW balance |
| `erp-golden-path.spec.ts` | erp | employee list 200 + seed employee + effectivePeriod |
| `operator-overview-composition.spec.ts` | all 5 | 5-card grid renders, no error banner |
| `domain-health-composition.spec.ts` | all 5 | 5 domains UP, no DOWN status |

**Post-MVP specs (added as later ADRs went runtime):**

| Spec | Proves |
|---|---|
| `observability-trace-tree.spec.ts` | ADR-018 D4 cross-product distributed trace tree (MONO-144) |
| `entitlement-trust-crossdomain.spec.ts` | ADR-019 entitlement-trust gate (acme-corp finance/wms entitled, scm/erp denied) (MONO-154) |
| `tenant-switch-rescope.spec.ts` | ADR-020 D4 active-tenant switch re-scopes the signed token (acme ↔ globex) (MONO-158) |
| `subscription-plane-separation.spec.ts` | ADR-023 D2 entitlement↔IAM plane separation — runtime suspend drops the domain from the re-issued token while the IAM assignment survives; reversible (MONO-207) |
| `tenant-admin-delegation.spec.ts` | ADR-024 step 3 tenant-admin delegation — a non-platform TENANT_ADMIN administers its own tenant (assign/scope/peer-appoint 2xx) but is denied cross-tenant (403 TENANT_SCOPE_DENIED) + escalating grants (403 ROLE_GRANT_FORBIDDEN); a TENANT_BILLING_ADMIN suspends/resumes its own subscription (200) but not a foreign tenant; SUPER_ADMIN unconstrained (net-zero) (MONO-210) |
| `iam-admin-source-ip-condition.spec.ts` | ADR-026 step 3 iam admin SOURCE_IP access condition (the 4th authorization gate) — with the admin-service allowlist ON (`ADMIN_ACCESS_SOURCE_IP_CIDRS`), an RBAC-granted admin mutation from an out-of-range source IP is gated (403 ACCESS_CONDITION_UNMET, not executed) while the same mutation from an in-range source IP proceeds (201/204), and reads are never gated even from the blocked IP (mutation-only); source IP set per-request via X-Forwarded-For; allowlist covers RFC1918+loopback so the existing suite is unaffected (net-zero) (MONO-221) |
| `iam-admin-resource-tag-condition.spec.ts` | ADR-029 step 4 iam admin RESOURCE_TAG access condition (the closed enum's 3rd/final type) — with the admin-service configured `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN=protected`, a role mutation on a `protected`-tagged operator is gated (403 ACCESS_CONDITION_UNMET) while the SAME mutation on an untagged operator proceeds (200); the discriminant is the TARGET resource's tag (per-resource) so the gate is net-zero for every other operator — the deterministic federation proof TIME_WINDOW (global clock) could not be; composes AND-only with SOURCE_IP (MONO-228) |

## CI trigger

`.github/workflows/federation-hardening-e2e.yml` — nightly cron `0 19 * * *` UTC (KST 04:00, 1h offset from nightly-e2e.yml) + `workflow_dispatch`. NO push trigger (ADR-018 D1 explicit: nightly + on-demand sufficient).

## Host port registry

Host-published ports across the base stack + all demo overlays are catalogued in
[`docker/HOST-PORTS.md`](docker/HOST-PORTS.md). **Consult and update it before assigning a host
port in any overlay** — it is the single allocation table that prevents the silent collisions
overlays have hit before (`ledger` ↔ `erp-read-model` on `18097`).

## Local dev loop

### Prerequisites

```bash
# 1. Build boot jars (from repo root)
./gradlew \
  :projects:iam-platform:apps:auth-service:bootJar \
  :projects:iam-platform:apps:account-service:bootJar \
  :projects:iam-platform:apps:admin-service:bootJar \
  :projects:finance-platform:apps:account-service:bootJar \
  :projects:wms-platform:apps:master-service:bootJar \
  :projects:scm-platform:apps:procurement-service:bootJar \
  :projects:erp-platform:apps:masterdata-service:bootJar \
  :projects:platform-console:apps:console-bff:bootJar

# 2. Build console-web Next.js standalone
cd projects/platform-console/apps/console-web && pnpm install && pnpm build && cd -
```

### Bring the stack up

```bash
# from repo root
bash scripts/fed-e2e-up.sh          # POSIX
.\scripts\fed-e2e-up.ps1            # Windows

BUILD=1 bash scripts/fed-e2e-up.sh  # rebuild images
NO_SEED=1 bash scripts/fed-e2e-up.sh
```

The script starts IAM + datastores, applies `seed.sql`, then runs a bare
`up -d` for everything else the compose files declare, applies the domain
read-model seeds, and finally **asserts that every declared service has a
running container** — exiting non-zero and naming the offenders if not.

Do not hand-enumerate services to `up -d` here. That is how this stack lost
`victoriatraces`: the old procedure listed 14 of the 19 services the base
compose declares, so seven services exported OTLP spans to a host that never
existed and wrote 17.1GB of stack traces to their container logs before anyone
noticed (TASK-MONO-339). Adding a service to the compose file is now the only
step needed for it to be part of the stack.

Note the script passes `-p federation-hardening-e2e`. The compose files carry no
top-level `name:`, so a bare `docker compose -f …` run from `docker/` would take
its project name from the directory and build a second, parallel stack.

### Run Playwright suite

```bash
cd tests/federation-hardening-e2e
pnpm install
# Add auth-service to /etc/hosts (PC-FE-028 iter 7 pattern) — required for OIDC PKCE
echo "127.0.0.1 auth-service" | sudo tee -a /etc/hosts
CONSOLE_BASE_URL=http://localhost:3000 pnpm exec playwright test --project=chromium
```

### Teardown

```bash
cd tests/federation-hardening-e2e/docker
docker compose -p federation-hardening-e2e \
  -f docker-compose.federation-e2e.yml \
  -f docker-compose.federation-e2e.demo.yml down -v
```

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `CONSOLE_BASE_URL` | `http://localhost:3000` | Playwright baseURL |
| `E2E_CONSOLE_ORIGIN` | `http://localhost:3000` | Origin used in OIDC redirect |
| `E2E_OIDC_ISSUER_URL` | `http://auth-service:8081` | OIDC issuer for JWT iss validation |

## Architecture

- **Location**: root `tests/federation-hardening-e2e/` (D1 — cross-product scope, no single project owner)
- **Harness**: Playwright on PC-FE-019..031 foundation (D2 — same OIDC PKCE driver, same storageState pattern)
- **Scope**: 7 spec files MVP (D3 — 5 golden-path + 2 composition)
- **Credential rule**: per-domain as declared in `console-integration-contract.md` § 2.4.5/6/7/8 — byte-unchanged (ADR-018 § 3.1 zero-retrofit sixth confirmation)
