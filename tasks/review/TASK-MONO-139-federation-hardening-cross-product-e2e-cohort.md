# Task ID

TASK-MONO-139

# Title

Phase 8 Federation Hardening — Cross-Product E2E Cohort (MVP): root `.github/workflows/federation-hardening-e2e.yml` + 7 Playwright spec files (5 golden-path + Operator Overview composition + Domain Health composition) + docker-compose stack extension to all 5 backend domains + console-bff + console-web

# Status

review

# Owner

infra / qa

# Task Tags

- e2e
- phase-8
- federation-hardening
- ci
- playwright
- docker-compose

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

ADR-MONO-018 (Phase 8 Federation Hardening Architecture — ACCEPTED 2026-05-26 via TASK-MONO-138 #835 squash `b0128919`) D1-D3 finalised direction 의 **실 cohort 작성 (MVP)**: cross-product e2e suite 의 location (root `tasks/`) + trigger (new `.github/workflows/federation-hardening-e2e.yml` — nightly cron + workflow_dispatch) + harness (Playwright extended on PC-FE-019..031 foundation) + scope (5 domain golden-path × 1 + Operator Overview composition + Domain Health composition = 7 spec files).

본 task = ADR-018 § 3.3 Future-self **step 2** (3 execution task series 중 자연 sequencing leader; step 3 observability federation impl 이 본 task 의 e2e suite 가 trace tree 를 assert 하는 baseline 위에서 진행, step 4 multi-tenant isolation regression IT cohort 는 본 task 와 독립).

## Phase 8 gate (이미 충족 — ADR-018 § 1.1)

- `projects/platform-console/PROJECT.md` 자체 선언 "Phase 1~6 COMPLETE + Phase 7 LIVE"
- `service_types: [frontend-app, rest-api]` post-Phase-7 mutation
- 5/5 backend domains live: GAP + wms + scm + finance + erp
- console-bff D7 per-domain fan-out attribution baseline ON
- ADR-MONO-013 § History additive note count = 7 (ADR-018 ACCEPTED 까지)

## ADR-018 D1-D3 (CHOSEN-PROPOSED finalised at ACCEPTED, byte-unchanged)

| D | Decision | Mechanics |
|---|---|---|
| **D1** | root `tasks/` + new `federation-hardening-e2e.yml` | nightly cron + `workflow_dispatch`; suite location = root scope (not per-project) |
| **D2** | Playwright extended on PC-FE-019..031 harness | OIDC PKCE driver (`driveOidcPkceLogin`) + storageState pattern + trace `'on'` (post-MONO-133) + docker-compose orchestration |
| **D3** | golden path × 5 + Operator Overview + Domain Health composition | MVP = 7 spec files; per-domain producer specs NOT modified |

---

# Scope

## In Scope (impl PR)

### 1. New root e2e harness directory

새 root-level e2e harness directory 신설 — **`tests/federation-hardening-e2e/`** at repo root (NOT `projects/platform-console/apps/console-web/tests/e2e/` — cross-product scope 가 어느 단일 project 의 소유도 아니므로 root 보존). 구조:

```
tests/federation-hardening-e2e/
├── playwright.config.ts                            # NEW (Chromium project + trace 'on' + reporter github)
├── package.json                                    # NEW (devDependencies @playwright/test ^1.49.0 + tsx + types pin)
├── pnpm-lock.yaml                                  # NEW (locked Playwright deps)
├── tsconfig.json                                   # NEW (target ES2022 + strict)
├── .gitignore                                      # NEW (storage-state.json + playwright-report/ + test-results/)
├── fixtures/
│   ├── global-setup.ts                             # NEW (mints SUPER_ADMIN session via driveOidcPkceLogin, persists storageState)
│   ├── login.ts                                    # NEW (copies PC-FE-022/028 driveOidcPkceLogin + tenant cookie helpers — same OIDC PKCE flow)
│   ├── seed.sql                                    # NEW (multi-domain operator + 5 tenant rows: GAP + wms + scm + finance + erp)
│   └── seed-domains.sql                            # NEW (per-domain producer test data: wms warehouse + scm purchase order + finance account + erp employee — 1 row each, idempotent INSERT IGNORE)
├── specs/
│   ├── gap-golden-path.spec.ts                     # NEW (operator login → GAP operator registry list → assert credential rule § 2.4.5..)
│   ├── wms-golden-path.spec.ts                     # NEW (operator login → wms warehouse list → assert tenant_id ∈ {wms,*})
│   ├── scm-golden-path.spec.ts                     # NEW (operator login → scm purchase order list → tenant_id ∈ {scm,*})
│   ├── finance-golden-path.spec.ts                 # NEW (operator login → finance account read → tenant_id ∈ {finance,*})
│   ├── erp-golden-path.spec.ts                     # NEW (operator login → erp employee list/detail → tenant_id ∈ {erp,*})
│   ├── operator-overview-composition.spec.ts       # NEW (composition card grid renders 5-domain fan-out; per-card degrade visible when one domain paused; no shell blank)
│   └── domain-health-composition.spec.ts           # NEW (5-domain health attribution surfaces correctly when one downstream is forced 503 via docker compose pause)
└── docker/
    └── docker-compose.federation-e2e.yml           # NEW (extends platform-console e2e compose with wms-* + scm-* + erp-* services + per-domain mysql databases)
```

총 = **~16 new files**, 0 existing-file mutation outside the new workflow + new fixtures + new compose (per-domain producer specs / `console-integration-contract.md` § 2.4.5/6/7/8 / 모든 ADR byte-unchanged per ADR-018 § 3.1 zero-retrofit invariant).

### 2. New CI workflow

**`.github/workflows/federation-hardening-e2e.yml`** (NEW):

- **Trigger** (D1):
  - `schedule`: `'0 19 * * *'` UTC (KST 04:00, 기존 `nightly-e2e.yml` `0 18 * * *` + 1h 어긋남 — parallel CI 자원 분산)
  - `workflow_dispatch`: on-demand for diagnosis / release-gate verification
  - **NOT** `push: main` — root cross-product scope 가 every-push 비용 부담 과대 (nightly + on-demand 충분; CI-on-PR 는 path-filter 미통과)
- **Jobs**:
  - `federation-hardening-boot-jars` (timeout 25 min): `./gradlew :gap:bootJar :wms:bootJar :scm:bootJar :finance:bootJar :erp:bootJar :platform-console:console-bff:bootJar` → artifact upload (sibling pattern with `platform-console-boot-jars-nightly` job)
  - `federation-hardening-e2e-fullstack` (needs: boot-jars, timeout 50 min):
    1. Checkout
    2. Set up pnpm + Node 20 (cache)
    3. Install `tests/federation-hardening-e2e/` deps (`pnpm install --frozen-lockfile`)
    4. Download boot-jars artifact + restore canonical paths
    5. Build console-web (`pnpm --filter console-web build`)
    6. Start docker compose phase 1 (mysql + redis + auth-service + account-service + admin-service)
    7. Wait admin-service healthy (5 min budget)
    8. Apply seed.sql (multi-domain operator + 5 tenant rows)
    9. Start docker compose phase 2 (wms + scm + finance-account-service + erp + console-bff + console-web)
    10. Wait 5 producer services healthy (sequential, 5 × 4 min budget = 20 min worst case)
    11. Apply seed-domains.sql (post-Flyway per-domain test data)
    12. Wait console-web healthy
    13. Install Playwright chromium (`--with-deps`)
    14. Add `auth-service` to `/etc/hosts` (PC-FE-028 iter 7 pattern — sudo tee)
    15. Run Playwright e2e (7 specs, Chromium project, env vars: `CONSOLE_BASE_URL`, `E2E_AUTH_BASE_URL`, `E2E_ADMIN_BASE_URL`, per-domain `E2E_<DOMAIN>_BASE_URL`, `E2E_CONSOLE_BFF_URL`)
    16. Dump docker compose logs on failure (`--tail=500`)
    17. Stop docker compose (`down -v`)
    18. Upload Playwright report + trace artifact (`if: always()`, retention 7 days)

### 3. docker-compose.federation-e2e.yml extension

**`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** (NEW; bridges + reuses platform-console docker-compose.e2e.yml service definitions where possible via `extends:` 또는 image reuse, decision 은 impl agent 가 정함):

- **Phase 1** (이미 platform-console 에서 검증): mysql:8.0 (shared 9 schemas — auth_db / account_db / admin_db / finance_db + 신규 wms_db / scm_procurement_db / scm_inventory_visibility_db / erp_db / consolidated kafka_db) / redis:7-alpine / kafka:alpine-placeholder (BE-048 DNS-only) / auth-service / account-service / admin-service
- **Phase 2** (신규): finance-account-service (이미 platform-console e2e 에 있음, reuse) + **wms-master-service** + **wms-inbound-service** OR consolidated WMS gateway (impl agent 가 wms 의 v1 architecture 보고 정확한 service 선택; minimum = `tenant_id ∈ {wms,*}` 검증 가능한 read endpoint 1개 노출하는 service) + **scm-procurement-service** + **erp-masterdata-service** + console-bff + console-web

**Network**: pc-federation-e2e (bridge)

**Hostname mappings** (docker DNS, no Traefik; **TEMPLATE.md § Hostname allocation Local Network Convention 적용 외부 — e2e 컨테이너 내부 DNS 만 사용**):
- auth-service:8081 (GAP OIDC issuer — 모든 producer 의 `OIDC_ISSUER_URL` 가 이걸 가리킴)
- admin-service:8085 (GAP operator token-exchange, registry — `console-bff` 가 D4 GAP-domain leg 사용)
- finance-account-service:8080 (finance)
- wms-*-service:8080 (wms; 정확 service name 은 impl agent 가 wms architecture 보고 정함)
- scm-procurement-service:8080 (scm; 마찬가지)
- erp-masterdata-service:8080 (erp)
- console-bff:8080 + console-web:3000

**`console-bff` env wiring** (이전 e2e 에서 wms/scm/erp 는 `127.0.0.1:9` 비루팅 placeholder 였음 — 본 task 에서 **실 hostname** 으로 교체):
- `CONSOLE_BFF_OUTBOUND_GAP_BASE_URL=http://admin-service:8085`
- `CONSOLE_BFF_OUTBOUND_WMS_BASE_URL=http://wms-master-service:8080`
- `CONSOLE_BFF_OUTBOUND_SCM_BASE_URL=http://scm-procurement-service:8080`
- `CONSOLE_BFF_OUTBOUND_FINANCE_BASE_URL=http://finance-account-service:8080`
- `CONSOLE_BFF_OUTBOUND_ERP_BASE_URL=http://erp-masterdata-service:8080`

### 4. seed.sql + seed-domains.sql

- **`seed.sql`** (phase 1.5, applied after admin-service healthy / before phase 2):
  - auth_db.credentials INSERT — `e2e-super-admin@example.com` Argon2id (reuse PC-FE-022 V0014 dev seed hash)
  - admin_db.admin_operators INSERT — 1 row: `e2e-super-admin` (SUPER_ADMIN, `tenant_id='*'`)
  - admin_db.admin_operator_roles + admin_db.admin_roles INSERT — SUPER_ADMIN bind + `require_2fa=FALSE`
  - 5 producer schema CREATE DATABASE statements (mirror finance_db pattern from existing seed.sql section 1)
- **`seed-domains.sql`** (phase 2.5, applied post-Flyway per-domain):
  - wms_db: 1 warehouse row (`tenant_id='*'`, fixed UUID id matching spec payload)
  - scm_procurement_db: 1 purchase order row (`tenant_id='*'`)
  - finance_db.accounts + balances: reuse existing seed-finance.sql 1 row pattern
  - erp_db: 1 employee row + 1 department row + 1 cost-center row
  - All idempotent (`INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE id=id`)

### 5. Playwright specs (7 total)

각 spec 의 minimum assertion shape:

| Spec | Steps | Assertion |
|---|---|---|
| `gap-golden-path.spec.ts` | 1. driveOidcPkceLogin (SUPER_ADMIN) <br/>2. navigate `/console/gap/operators` <br/>3. assert page renders + at least one row | operator registry list 가 200 + 1+ row |
| `wms-golden-path.spec.ts` | login + navigate `/console/wms/warehouses` | wms warehouse list 가 200 + seed 의 1 row |
| `scm-golden-path.spec.ts` | login + navigate `/console/scm/purchase-orders` | scm PO list 200 + seed 의 1 row |
| `finance-golden-path.spec.ts` | login + navigate `/console/finance/accounts/<seed-id>` | finance account detail 200 + balances rendered (money string-integer-minor-units) |
| `erp-golden-path.spec.ts` | login + navigate `/console/erp/employees` | erp employee list 200 + seed 의 1 row + effectivePeriod rendered |
| `operator-overview-composition.spec.ts` | login + navigate `/console/dashboards/operator-overview` | 5-card grid renders + 5 도메인 모두 `ok` status (Phase 8 baseline) |
| `domain-health-composition.spec.ts` | login + navigate `/console/dashboards/domain-health` | 5-domain health attribution rendered + all 5 = UP. 후속 (out-of-scope): degrade path 검증 (한 도메인 force-pause). |

### 6. Documentation

- `tests/federation-hardening-e2e/README.md` (NEW): 실 cohort 의 dev-loop 사용법 (local `pnpm exec playwright test`, env var setup, docker-compose up 명령)
- `tasks/INDEX.md` ready → review 단계 lifecycle 갱신

### 7. Out of Scope (this task)

- **Observability federation impl** — OTel `trace_id` propagation strengthening at console-web SSR + console-bff fan-out + per-domain downstream. **별 task** (ADR-018 § 3.3 Future-self step 3; root `tasks/`, Sonnet).
- **Multi-tenant isolation regression IT cohort** — per-domain `TenantClaimValidator` IT (5 producers) + console-bff D6 tenant pass-through cross-tenant deny IT. **별 task series** (ADR-018 § 3.3 Future-self step 4; per-domain project-internal `tasks/` × 5 + platform-console-internal × 1, **Opus** per ADR-013 § D6 row 8 *"isolation → Opus"*).
- **Per-domain producer spec / contract 변경 0** (ADR-018 § 3.1 zero-retrofit sixth confirmation 보존; `console-integration-contract.md` § 2.4.5/6/7/8 byte-unchanged; ADR-006/007/013/014/015/016/017/018 byte-unchanged — HARDSTOP-04).
- **Existing platform-console e2e (PC-FE-016 + PC-FE-017) 변경 0** — platform-console-internal scope 보존, 본 cohort 와 ownership 분리 (D7 명시).
- **Trace tree assertion 강화** (7-span tree 가 VictoriaTraces 에 assemble) — observability federation impl (step 3) 의 일. 본 task 는 trace `'on'` enable + artifact upload 까지만 (e2e cohort 가 후속 step 3 의 가시성 baseline 확립).
- **Degrade path (composition spec 의 force-pause)** — MVP 에 포함 안 함 (D3 명시: "MVP = 7 spec files"; degrade scenarios 는 MVP 안정 후 expand). composition spec 은 5-domain happy path 만.
- **agent memory 동기화** = repo task 외부 (dispatcher 가 본 task close chore 후 직접; sibling MONO-126/138 동형).

---

# Acceptance Criteria

1. **New workflow `.github/workflows/federation-hardening-e2e.yml`** 신설 — trigger = `schedule '0 19 * * *'` + `workflow_dispatch`; **NOT** push-trigger; 2 jobs (boot-jars + e2e-fullstack); nightly + dispatch 모두 GREEN 1회 이상.
2. **`tests/federation-hardening-e2e/` 디렉토리** 신설 — package.json + playwright.config.ts + tsconfig.json + fixtures (global-setup, login, seed.sql, seed-domains.sql) + 7 spec files + docker-compose.federation-e2e.yml + README.md.
3. **5 golden-path specs** (gap / wms / scm / finance / erp) — 각 spec = operator login → 도메인 primary read screen → 200 OK + 1+ seed row assertion. 각 도메인의 § 2.4.5/6/7/8 per-domain credential rule 가 end-to-end 동작 (byte-unchanged 검증).
4. **Operator Overview composition spec** — 5-card grid renders + 5 도메인 `ok` status (Phase 8 happy path baseline; degrade path = MVP 외).
5. **Domain Health composition spec** — 5-domain health attribution rendered + all 5 UP (degrade path = MVP 외).
6. **docker-compose.federation-e2e.yml** — 9+ services (mysql / redis / kafka-placeholder / auth-service / account-service / admin-service / wms-* / scm-* / finance-account-service / erp-* / console-bff / console-web) booted via phased start (Phase 1 = GAP + admin; Phase 1.5 = seed.sql; Phase 2 = 5 producer + bff + web; Phase 2.5 = seed-domains.sql). 모든 health 5분 안에 GREEN.
7. **Trace artifact upload (PC-FE-028 / MONO-133 패턴 답습)** — `playwright-report/` + `test-results/` 모두 `if: always()` upload, 7-day retention.
8. **HARDSTOP-04 검증** — `git diff` 로 per-domain producer spec / `console-integration-contract.md` § 2.4.5/6/7/8 / 모든 ADR (013/014/015/016/017/018/006/007/002/003a) byte-unchanged. 본 task 의 변경 = root `tests/federation-hardening-e2e/` 신설 + `.github/workflows/federation-hardening-e2e.yml` 신설 + INDEX + lifecycle 만.
9. **ADR-018 § 3.1 zero-retrofit sixth confirmation 보존** — per-domain producer / `console-integration-contract.md` byte-unchanged; 본 cohort 의 spec assertions 가 existing § 2.4.5/6/7/8 endpoints 만 호출 (새 producer endpoint 0).
10. **Lifecycle**: ready → review (impl PR 머지) → done (close chore PR). 루트 strict PR Separation Rule (spec / impl / close chore 분리).
11. **CI 객관 검증** (BE-303 3-dim): impl PR 머지 시점 `gh pr view` `state=MERGED` + `gh pr checks` failing=0 + `git log origin/main` tip = squash commit; 머지 후 nightly dispatch 1회 + workflow_dispatch 1회 모두 GREEN 객관 기록.
12. **본 task ACCEPTED 머지 후** = step 3 (observability federation impl) + step 4 (isolation regression IT cohort) 의 dependency-correct authorization base 확립 (ADR-018 § 3.3 Future-self chain 의 단계적 진행 base).

---

# Related Specs

> Target = root `tests/federation-hardening-e2e/` + `.github/workflows/federation-hardening-e2e.yml`. Governing: ADR-MONO-018 자체 (Phase 8 § 3.3 step 2) + ADR-MONO-013 § D6 row 8 (parent phase) + ADR-MONO-017 D4 + ADR-MONO-006/007 observability stack (D4 reuse, not amend) + `platform/hardstop-rules.md` HARDSTOP-04.

- [docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md](../../docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md) — governing ADR; D1/D2/D3 (location/trigger/harness/scope MVP) finalised byte-unchanged at ACCEPTED 2026-05-26
- [docs/adr/ADR-MONO-013-platform-console-foundation.md § D6 row 8](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — Phase 8 raw description (parent)
- [docs/adr/ADR-MONO-017-platform-console-bff-architecture.md § D4 + § D6 + § D7](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) — `console-bff` D4 per-domain credential rule (HARD INVARIANT) + D6 tenant pass-through + D7 per-domain fan-out attribution baseline
- [projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.5/6/7/8](../../projects/platform-console/specs/contracts/console-integration-contract.md) — per-domain credential rule (wms/scm/finance/erp) — byte-unchanged invariant
- [projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts](../../projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts) — `driveOidcPkceLogin` 패턴 reference (PC-FE-022 + PC-FE-028 iter 7)
- [projects/platform-console/apps/console-web/tests/e2e/fixtures/global-setup.ts](../../projects/platform-console/apps/console-web/tests/e2e/fixtures/global-setup.ts) — globalSetup pattern reference
- [.github/workflows/nightly-e2e.yml `platform-console-e2e-fullstack` job](../../.github/workflows/nightly-e2e.yml) — phased start + seed + Playwright + trace artifact upload reference (target sibling pattern)
- [projects/platform-console/apps/console-web/tests/e2e/docker-compose.e2e.yml](../../projects/platform-console/apps/console-web/tests/e2e/docker-compose.e2e.yml) — 7-service docker-compose reference (target sibling pattern; extend with wms/scm/erp)
- [tasks/done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md](../../tasks/done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md) — phase-2.5 seed pattern (post-Flyway per-producer data) reference
- [tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md](../../tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md) — trace `'on'` + `if: always()` upload pattern reference
- [tasks/done/TASK-MONO-138-adr-mono-018-accepted-transition.md](../../tasks/done/TASK-MONO-138-adr-mono-018-accepted-transition.md) — 직전 단계 (ADR-018 ACCEPTED transition)

# Related Skills

- `.claude/skills/INDEX.md` — Sonnet 권장 (ADR-013 § D6 row 8 + ADR-018 D7 명시); `implement-task` skill 적용 가능 (standard 7-step workflow).

---

# Related Contracts

- **None changed** (per-domain producer specs / `console-integration-contract.md` § 2.4.5/6/7/8 / 모든 ADR byte-unchanged per ADR-018 § 3.1 zero-retrofit invariant).
- **Cross-referenced**: 5 도메인의 read endpoints (existing) — wms `/api/wms/warehouses` / scm `/api/scm/purchase-orders` / finance `/api/finance/accounts/{id}` / erp `/api/erp/employees` + console-bff `/api/console/dashboards/operator-overview` + `/api/console/dashboards/domain-health` — 모두 main 라이브 상태에서 본 cohort 가 verification target 으로 호출. 본 cohort 는 spec assertion 만; endpoint 의 contract / signature 변경 0.

---

# Target Service

- N/A (root-level cross-product cohort, monorepo-level).
- Cross-referenced services (existing, byte-unchanged): GAP `admin-service` + wms `master-service` (or equivalent) + scm `procurement-service` + finance `account-service` + erp `masterdata-service` + platform-console `console-bff` + `console-web`.

---

# Architecture

- **Cross-product e2e cohort** = root `tests/federation-hardening-e2e/` (D1 location). New top-level dir under repo root (sibling to `projects/`, `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/`, `docs/`). 의미: cross-product scope 는 어느 단일 project 의 소유도 아님; root 보존이 정확한 ownership.
- **Trigger** (D1) = nightly cron + workflow_dispatch — push-trigger 부재 = CI 자원 절약 + cross-product scope = nightly regression channel 충분.
- **Harness** (D2) = Playwright extended on PC-FE-019..031 foundation — 동일 OIDC PKCE driver / 동일 storageState 패턴 / 동일 trace `'on'` + `if: always()` upload (MONO-133) / 동일 phased docker-compose orchestration (MONO-132).
- **Scope** (D3) = 7 spec files (5 golden-path + 2 composition) — MVP first principle. 후속 expansion 은 별 task (예: degrade path / full surface / cross-tenant deny path).
- **Test data** = 5 producer 의 minimum-shape seed (1 row each). 본 cohort 가 read-only verification (D3 + § 3.1 zero-retrofit 보존; 새 mutation/producer endpoint 0).

---

# Implementation Notes

- **Recommended impl model = Sonnet 4.6** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — ADR-MONO-013 § D6 row 8 explicit prescription + ADR-MONO-018 D7 explicit prescription (Sonnet cohort 명시; multi-tenant isolation regression IT cohort 만 Opus). 본 task = cross-product e2e cohort = Sonnet.
- **Dispatch via `Agent(subagent_type="qa-engineer", model="sonnet", ...)`** OR `Agent(subagent_type="devops-engineer", model="sonnet", ...)` — 본 task 의 표면 = qa (Playwright spec authoring) + devops (workflow + docker-compose) 양쪽 영역. qa-engineer 가 primary, devops-engineer 가 workflow + compose 부분 dispatch. **CLAUDE.md "Recommending Tasks and Dispatching Agents"** 의 `model="sonnet"` 명시 pass.
- **CI iteration 예상**: 본 task 의 docker-compose stack = 9+ services + 5 producer Flyway boot (각 5분 budget) → 첫 dispatch 시 CI fail 확률 높음 (TASK-MONO-132 chain 의 5-layer cycle 패턴 = PC-FE-023 → PC-FE-024 → MONO-132 → PC-FE-025 → PC-FE-026 모두 phase ordering / cache / boot timing 학습 사례). 본 task 도 동일 cycle pattern 예측 → 3-5 cycle CI iteration 예상; 각 cycle 별 surface 명시 보고 (sibling MONO-132 close chore 메타 ② 답습).
- **Boot jar set 확장**: 기존 `platform-console-boot-jars-nightly` = 5 boot jars (auth + account + admin + finance-account + console-bff). 본 task = +wms (master OR ...) + scm (procurement OR ...) + erp (masterdata) = 8 boot jars. boot-jars job timeout = 25 분 (5 jars 15 분 budget + 3 jars 10 분 budget).
- **HARDSTOP-04 discipline**: per-domain producer src/spec/contract / ADR / `console-integration-contract.md` 모두 byte-unchanged. `git diff` 객관 검증.
- **CLAUDE.md "Branch name constraint"**: branch name MUST NOT contain `master` substring. 본 task spec PR branch = `chore/mono-139-federation-e2e-spec` ✓; impl PR branch 예 = `chore/mono-139-federation-e2e-impl` ✓; close chore = `chore/mono-139-federation-e2e-close` ✓.
- **BE-303 3-dim merge verification + BE-299 re-stage check 각 단계 적용** (sibling MONO-138 / MONO-137 / MONO-126 등).
- **CI path-filter consideration** (project_ci_path_filter_074_075_quirk 메모리): 본 task 의 `.github/workflows/federation-hardening-e2e.yml` NEW = trigger schema 자체 변경; `paths-filter` 와 무관 (cron + workflow_dispatch trigger 만). 본 task 의 impl 자체는 `.github/workflows/*.yml` + `tests/federation-hardening-e2e/**` + 신규 root 파일 만 → `code-changed` filter 가 매치 (full 20-job matrix activated for impl PR self-CI). nightly cron / dispatch 만 federation-hardening-e2e-fullstack 가 run.

---

# Edge Cases

1. **wms / scm / erp 의 boot jar build 실패** (e.g. Flyway migration drift since v1 publish) → cycle 1 surface 가능; mitigation = sibling MONO-132 의 "phase ordering" 진단 + Flyway 의존성 chain 분석. 본 task 의 boot-jars job 가 separate (e2e-fullstack 이전 단계 분리) → fail-fast.
2. **per-domain producer service 의 e2e endpoint shape mismatch** — 본 cohort 가 호출하는 read endpoint URL 이 console-integration-contract.md § 2.4.5/6/7/8 와 byte-unchanged 인지 spec phase 에 grep 검증; impl agent 가 contract 와 actual implementation 의 drift 발견 시 STOP + 사용자 보고 (HARDSTOP-04 + zero-retrofit invariant).
3. **5 producer 의 Flyway migration 누적 시간** — 각 5분 budget × 5 = 25분 worst-case (parallel boot 가능 시 cycle compress); 본 cohort 의 wait-health step 가 budget 확보 필요.
4. **docker-compose hostname 충돌 (wms-master-service:8080)** — 기존 `platform-console` e2e compose 의 finance-account-service:8080 + console-bff:8080 와 동일 port 충돌 위험; mitigation = e2e 컨테이너는 internal network DNS 만 사용 (host port mapping 본 cohort 에서 0 또는 unique offset 사용). Hostname 만 producer 별 unique (impl agent 가 docker-compose network DNS resolution 으로 처리).
5. **`auth-service` `/etc/hosts` 추가의 GitHub Actions runner permission** — PC-FE-028 iter 7 패턴 답습 (sudo tee -a /etc/hosts). runner = ubuntu-latest 표준.
6. **Trace artifact size 폭증** — 7 specs × Chromium trace `'on'` 가 worst-case 200MB+/run; mitigation = retention 7 days 명시 + GitHub Actions artifact 60GB quota 모니터링. nightly + dispatch 만 trigger → daily artifact 한도 안.
7. **Operator Overview composition spec 의 5-domain "ok" assertion 의 환경 신뢰성** — 5 producer 모두 happy path = 모든 도메인 boot + Flyway + seed 완료. cycle 1 fail 가능성 큰 단계; degrade path 는 MVP 외 (scope-out, AC-4 명시).
8. **nightly cron 시간 충돌** (기존 `nightly-e2e.yml` cron `0 18 * * *` UTC = KST 03:00 + 본 `federation-hardening-e2e.yml` `0 19 * * *` UTC = KST 04:00) — 1시간 차이로 동시 보트 자원 충돌 회피.

---

# Failure Scenarios

1. **per-domain producer spec / contract 변경 leak** → reject; ADR-018 § 3.1 zero-retrofit sixth confirmation + HARDSTOP-04 + `console-integration-contract.md` § 2.4.5/6/7/8 byte-unchanged invariant 위반.
2. **ADR-013/014/015/016/017/018/006/007/002/003a 변경 leak** → reject; HARDSTOP-04.
3. **존재 안 하는 producer endpoint 호출** (impl agent 가 contract drift 발견 + spec assertion modify) → STOP + 사용자 보고. zero-retrofit 위반 surface = 별 fix-task 의 일.
4. **CI fail / cycle 누적 > 7 cycles** → STOP + 사용자 보고 (sibling MONO-132 5-layer cycle 의 honest reporting 패턴). 무한 retry 금지.
5. **observability federation impl / multi-tenant isolation IT cohort leak 가 본 PR 에 포함** → reject; ADR-018 § 3.3 Future-self step 3/4 의 별 task series 의 일.
6. **Playwright spec assertion 가 D3 명시 7 spec 외 추가** (예: degrade path, full surface) → reject; MVP first principle (D3 명시).
7. **trace artifact upload 가 `if: failure()` only** (MONO-133 deviation) → reject; `if: always()` 명시 (D2 trace 'on' + always-upload baseline).
8. **CI 자원 폭증** (e.g. push-trigger 추가) → reject; D1 명시 trigger = nightly + dispatch only.

---

# Verification

- `git diff` confirms: only root `tests/federation-hardening-e2e/` (NEW 디렉토리 + ~14 new files) + `.github/workflows/federation-hardening-e2e.yml` (NEW) + `tasks/INDEX.md` + task lifecycle file modified.
- ADR 모두 (013/014/015/016/017/018/006/007/002/003a/003b/003) byte-identical (`git diff --stat docs/adr/`).
- `console-integration-contract.md` § 2.4.5/6/7/8 byte-identical.
- per-domain producer src/spec/contract 모두 byte-identical (`git diff --stat projects/`).
- **CI 객관 검증** (impl PR merge 후):
  - (a) `gh pr view <N> --json state,mergedAt,mergeCommit,statusCheckRollup` → `state=MERGED` + 0 failing
  - (b) `git log origin/main` tip = squash commit
  - (c) post-merge: nightly cron 1회 + workflow_dispatch 1회 모두 GREEN 객관 기록 (artifact upload + spec PASS 7/7)
- **Spec PASS count**: 7/7 GREEN required (5 golden-path + 2 composition).

---

# Definition of Done

- [ ] `.github/workflows/federation-hardening-e2e.yml` NEW — trigger schedule + dispatch + 2 jobs; 첫 nightly + 첫 dispatch GREEN.
- [ ] `tests/federation-hardening-e2e/` NEW — playwright.config.ts + package.json + tsconfig.json + fixtures (global-setup, login, seed.sql, seed-domains.sql) + 7 specs + docker-compose.federation-e2e.yml + README.md.
- [ ] 7 Playwright specs PASS (5 golden-path + 2 composition).
- [ ] docker-compose.federation-e2e.yml 9+ services healthy in phased start.
- [ ] Trace artifact upload `if: always()` + 7-day retention.
- [ ] HARDSTOP-04 검증: per-domain producer / console-integration-contract / 모든 ADR byte-unchanged.
- [ ] Lifecycle ready → review → done (3-PR sequence per root strict PR Separation Rule).
- [ ] Cross-references resolve.
- [ ] BE-303 3-dim merge verification + BE-299 re-stage check 각 단계 적용.
- [ ] 본 task ACCEPTED 머지 후 = ADR-018 § 3.3 step 3 (observability federation impl) + step 4 (isolation regression IT cohort) 의 dependency-correct authorization base 확립.
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet 4.6** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — ADR-MONO-013 § D6 row 8 + ADR-MONO-018 D7 explicit prescription. CLAUDE.md "Recommending Tasks and Dispatching Agents" 의 `model="sonnet"` 명시 dispatch.
- **분량**: large — ~16 new files + 1 new workflow + 5 producer boot integration. 예상 3-5 cycle CI iteration (sibling MONO-132 5-layer chain 답습 가능성).
- **dependency**: 선행 = ADR-018 ACCEPTED merged (TASK-MONO-138 #835 main `b0128919`, gate 충족 2026-05-26). 후속 = ADR-018 § 3.3 step 3 (observability federation impl) + step 4 (isolation regression IT cohort × 6).
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR / impl PR / close chore distinct.
- **user-explicit intent provenance**: 사용자 first message 2026-05-26 *"후속 진행"* + AskUserQuestion option A *"TASK-MONO-139 cross-product e2e cohort (단일, recommended)"* 선택 (description: *"ADR-018 식 3.3 step 2 = 자연 sequencing leader. spec PR 알파 후 (Sonnet 권장 per ADR-013 식 D6 row 8) impl 도 이어서 진행"*). § D8.1 acceptable form *"Phase 8 federation hardening 시작"* 의 실 cohort 작성 변형 = 명시 confirm form.
