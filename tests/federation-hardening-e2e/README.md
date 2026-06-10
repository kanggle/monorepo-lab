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

## CI trigger

`.github/workflows/federation-hardening-e2e.yml` — nightly cron `0 19 * * *` UTC (KST 04:00, 1h offset from nightly-e2e.yml) + `workflow_dispatch`. NO push trigger (ADR-018 D1 explicit: nightly + on-demand sufficient).

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

### Start Phase 1 (GAP stack)

```bash
cd tests/federation-hardening-e2e/docker
docker compose -f docker-compose.federation-e2e.yml up -d --build \
  mysql redis kafka auth-service account-service admin-service
```

### Wait for admin-service healthy, then apply seed.sql

```bash
docker compose -f docker-compose.federation-e2e.yml exec -T mysql \
  mysql -uroot -prootpass < ../fixtures/seed.sql
```

### Start Phase 2 (producers + console)

```bash
docker compose -f docker-compose.federation-e2e.yml up -d --build \
  wms-postgres scm-postgres \
  wms-master-service scm-procurement-service finance-account-service erp-masterdata-service \
  console-bff console-web
```

### Apply domain seeds (after all producers healthy)

```bash
# MySQL domains (finance + erp)
docker compose -f docker-compose.federation-e2e.yml exec -T mysql \
  mysql -uroot -prootpass < ../fixtures/seed-domains.sql

# WMS (PostgreSQL)
docker compose -f docker-compose.federation-e2e.yml exec -T wms-postgres \
  psql -U master -d master_db < ../fixtures/seed-wms.sql

# SCM (PostgreSQL)
docker compose -f docker-compose.federation-e2e.yml exec -T scm-postgres \
  psql -U scm -d scm_procurement < ../fixtures/seed-scm.sql
```

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
docker compose -f docker-compose.federation-e2e.yml down -v
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
