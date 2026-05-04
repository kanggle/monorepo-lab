# Task ID

TASK-MONO-040

# Title

scm-platform 프로젝트 부트스트랩 (모노레포 5번째 프로젝트)

# Status

ready

# Owner

backend

# Task Tags

- bootstrap
- monorepo
- new-project
- scm

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

`scm` 도메인의 5번째 모노레포 프로젝트 `scm-platform` 의 **skeleton 부트스트랩**.

현재까지 모노레포는 4 프로젝트 (wms / ecommerce / GAP / fan-platform) 가 동거하며, 공통규칙 정리 시리즈 (TASK-MONO-029 ~ 039) 로 라이브러리 정합성을 마무리한 직후. `scm` 도메인은 이미 taxonomy / dispatch / activation 3 카탈로그에 등록되어 있고 (TASK-MONO-029 audit 시점), TEMPLATE.md 의 hostname 표·GAP IdP 예시에도 미리 예고되어 있다 — 즉 모든 카탈로그 사전 작업이 끝난 상태에서 신규 프로젝트를 추가하는 첫 사례.

[**ADR-MONO-002**](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) (ACCEPTED 2026-05-04) 가 본 부트스트랩의 정당성을 명시적으로 부여한다 — D1: 5 프로젝트 동거를 Phase 4 catalyst 로 채택, D2: 첫 도메인 = scm (wms 시너지 + 구현 난이도 중 + 첫 도메인 churn 최소화). 본 task 는 ADR 의 § 5 Migration/Implementation Plan 의 후속 task 1번으로 직접 호명되어 있다. 또한 ADR 의 D3 (Template 레포 실제 추출 시점은 별도 ADR-MONO-003 candidate 으로 deferred) 가 본 부트스트랩 결과 (라이브러리 churn 안정 평가) 에 의존하므로, 본 task 는 Phase 4 trigger 의 실 데이터 수집 의의를 가진다.

본 task 는 TEMPLATE.md "Option A — Greenfield" Step 1~12 를 그대로 따라 디렉토리 구조, `PROJECT.md`, `tasks/INDEX.md`, `docker-compose.yml` (Traefik hostname routing), `.env.example`, 루트 `settings.gradle` / `package.json` 변경, `README.md` 까지의 **인프라 skeleton** 만 작성한다. 첫 service skeleton (`gateway-service`, `procurement-service` 등) 은 후속 TASK-SCM-BE-001 으로 분리, GAP V0013 시드는 별도 후속 TASK-MONO 로 분리한다.

또한 `rules/domains/scm.md` 가 아직 on-demand 미작성 상태이므로 본 task 의 PR 에서 함께 신설한다 (CLAUDE.md On-Demand Rule Policy — 신규 도메인 선언 PR 에 detailed rule 파일이 함께 존재해야 함).

---

# Scope

## In Scope

### 1. 프로젝트 디렉토리 트리 생성

```
projects/scm-platform/
├── apps/                                       # 비어있음 (.gitkeep)
├── specs/
│   ├── contracts/
│   │   ├── http/
│   │   └── events/
│   ├── services/
│   ├── features/
│   ├── use-cases/
│   └── integration/                            # GAP integration spec 자리 (후속)
├── tasks/
│   ├── INDEX.md
│   ├── backlog/
│   ├── ready/
│   ├── in-progress/
│   ├── review/
│   ├── done/
│   └── archive/
├── knowledge/
├── docs/
├── infra/
├── PROJECT.md
├── README.md
├── docker-compose.yml
├── .env.example
└── build.gradle                                # placeholder
```

각 빈 디렉토리에는 `.gitkeep` 배치.

### 2. `PROJECT.md` 작성

frontmatter:

```yaml
---
name: scm-platform
domain: scm
traits: [transactional, integration-heavy, batch-heavy]
service_types: [rest-api, event-consumer, batch-job]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---
```

prose 섹션 — fan-platform / wms-platform `PROJECT.md` 구조를 따름:

- **Purpose** — 공급망 통합 백엔드 플랫폼. 조달 → 생산 → 물류 → 정산 cross-functional 흐름. wms 와의 차이 (단일 창고 vs cross-warehouse 가시성), erp 와의 차이 (전사 기간계 vs 공급망 흐름).
- **Domain Rationale** — 왜 scm 인지 (taxonomy 정의 인용 + 자체 해석).
- **Trait Rationale** — 각 trait 의 채택 근거.
  - transactional: PO 발행 / ASN 입고 / 정산 확정 등 멱등 + 강한 일관성
  - integration-heavy: 다수 supplier, 다수 carrier, ERP, wms (모노레포 내부 가능), 결제 시스템 등 외부 연동 다수
  - batch-heavy: 야간 정산 / 주기 수요예측 / 월 reconciliation 등 배치 워크로드 핵심
- **Service Map (v1)** — 부트스트랩 시점에 의도만 명시 (skeleton 미생성):
  - gateway-service (rest-api) — 엣지 라우팅, OIDC token 검증, `tenant_id=scm` 게이트
  - procurement-service (rest-api) — PO / Supplier / ASN inbound
  - inventory-visibility-service (rest-api, read-only) — cross-node 재고 가시성
- **Service Map (v2 deferred)** — demand-planning-service (batch), logistics-service, settlement-service (batch), notification-service, admin-service.
- **GAP IdP Integration** — TEMPLATE.md "GAP IdP Integration Pattern" Step 1~5 따름. tenant_id=scm. 상세는 `specs/integration/gap-integration.md` (TASK-SCM-BE-001 시 작성).
- **Local Network** — `scm.local` 호스트네임 (TEMPLATE.md hostname 표 사전 예약).
- **Out of Scope** — 명시적 제외: wms (단일 창고 운영은 별도 wms-platform 이 담당), erp (전사 회계·HR 미포함), data-intensive (포트폴리오 규모에서 TB+ 데이터 없음 — 추후 필요 시 trait 추가), regulated/audit-heavy (금융급 감사는 v1 범위 밖, 단 PO/정산 audit trail 은 도메인 자체 요구로 application 레벨 구현).
- **Overrides** — 현재 명시적 override 없음.

### 3. `tasks/INDEX.md` 작성

`projects/fan-platform/tasks/INDEX.md` 와 동일 구조. Task Type prefix:

- `TASK-SCM-BE-XXX` — backend
- `TASK-SCM-INT-XXX` — cross-service integration / E2E
- (frontend 미정 시점이므로 TASK-SCM-FE-XXX 는 prefix 만 declare 후 v2+ 활성)

PR Separation Rule (spec PR / impl PR / chore PR) 명시.

### 4. `docker-compose.yml` 작성

Traefik hostname routing (TEMPLATE.md § Local Network Convention):

- `gateway-service` placeholder: build context 가 아직 존재하지 않으므로 service 정의는 두되 첫 service skeleton 생성 전까지 `image:` 만 적고 `build:` 줄 코멘트 처리 → TASK-SCM-BE-001 에서 활성화. 또는 service 자체를 코멘트 처리 + 헤더 주석으로 안내. (둘 중 하나 선택, 후자가 더 깨끗 — fan-platform `docker-compose.yml` v1 초기 커밋 패턴 참조)
- 백킹 서비스 (postgres / redis / kafka) 는 v1 placeholder 로 정의만 두되 `expose:` 만 사용 (host port 노출 금지).
- 네트워크: `scm-platform-net` (bridge) + `traefik-net` (external).
- Traefik labels (gateway-service 활성화 시점):
  - `traefik.enable=true`
  - `traefik.docker.network=traefik-net`
  - `traefik.http.routers.scm-platform.rule=Host(\`scm.local\`)`
  - `traefik.http.routers.scm-platform.entrypoints=web`
  - `traefik.http.services.scm-platform.loadbalancer.server.port=8080`

### 5. `.env.example` 작성

```bash
# Hostname (Traefik routing — no PORT_PREFIX)
PROJECT_HOSTNAME=scm.local

# GAP OIDC (Resource Server)
OIDC_ISSUER_URL=http://gap.local
JWT_JWKS_URI=http://gap.local/oauth2/jwks

# Postgres / Redis / Kafka — placeholder for v1 service skeleton
POSTGRES_USER=scm
POSTGRES_PASSWORD=scm
POSTGRES_DB=scm

REDIS_PASSWORD=

CORS_ALLOWED_ORIGINS=http://scm.local
```

### 6. 루트 `settings.gradle`

apps 가 비어있으므로 include 블록은 **추가하지 않음** (gradle 가 빈 모듈을 거부). 첫 service skeleton 등장 시점에 TASK-SCM-BE-001 가 함께 갱신.

대신 `projects/scm-platform/build.gradle` placeholder 는 wms-platform 패턴 (헤더 코멘트만) 으로 작성 — 향후 multi-app 공통 plugin 등록 자리 표시.

### 7. 루트 `package.json` shortcut scripts

```jsonc
{
  "scripts": {
    "scm:up":     "docker compose --project-directory projects/scm-platform up -d",
    "scm:down":   "docker compose --project-directory projects/scm-platform down",
    "scm:ps":     "docker compose --project-directory projects/scm-platform ps",
    "scm:logs":   "docker compose --project-directory projects/scm-platform logs -f",
    "scm:docker": "docker compose --project-directory projects/scm-platform"
  }
}
```

### 8. `rules/domains/scm.md` 작성 (On-Demand Rule)

`rules/domains/wms.md` / `rules/domains/fan-platform.md` 구조 답습. 섹션:

- Scope — scm 도메인 범위 정의
- Bounded Contexts (표준) — Procurement / Demand / Logistics / Inventory Visibility / Settlement / Supplier / Admin
- Ubiquitous Language — PO, ASN, Supplier, Demand Forecast, Reorder Point, Lead Time, Settlement, Reconciliation, Catalog Sync, Carrier 등
- Standard Error Codes — `PO_NOT_FOUND`, `PO_ALREADY_CONFIRMED`, `ASN_QUANTITY_MISMATCH`, `SUPPLIER_INACTIVE`, `SETTLEMENT_PERIOD_LOCKED`, `DEMAND_FORECAST_STALE` 등 (도메인 특유)
- Integration Boundaries — 외부 (Supplier ERP, Carrier API, Bank, ERP, wms-platform), 내부 (gateway → 내부 서비스 호출 패턴)
- Mandatory Rules — multi-leg 흐름 일관성, supplier idempotency key, batch reprocessing, settlement period lock, audit trail for state transitions
- Forbidden Patterns — wms 단일 창고 로직을 scm context 에 직접 결합 (wms-platform 에서 흡수해야 함), supplier credential 평문 저장, settlement 결과 mutable 수정
- Required Artifacts — `specs/services/<s>/architecture.md` (Hexagonal/Layered 선언), `specs/contracts/events/` (procurement.po.confirmed / inventory.snapshot.published 등)
- Interaction with Common Rules — 공통 rules 위반 없음, 별도 override 불필요
- Checklist — 신규 service 생성 시 점검 사항 (tenant_id 격리, idempotency, batch restartability, supplier client circuit breaker)

**Library 경계 엄수**: 본 파일은 라이브러리 (`rules/`) 이므로 구체 service 명 (`procurement-service`) 직접 기재 금지. bounded context 이름 (`Procurement` context) 또는 `<settlement-service>` placeholder 사용.

### 9. `README.md` 작성

`projects/fan-platform/README.md` 구조 답습:

- 프로젝트 한 줄 소개 (공급망 통합 플랫폼)
- 도메인·traits 표
- v1 service map (skeleton 단계 명시)
- Local dev quick start (`pnpm traefik:up` 선행 → `pnpm scm:up`)
- hostname (`http://scm.local`) — hosts 파일 한 번 등록 안내
- known limitations (v1 미발행, GAP V0013 seed 후속, frontend 없음)
- 참조 링크 (PROJECT.md, GAP integration pattern)

### 10. 루트 `README.md` dev hosts 안내 갱신

`scm.local` 을 hosts 파일 추가 안내 라인에 포함 (이미 TEMPLATE.md 본문에는 포함됨, 루트 README 의 "One-time setup" 섹션이 있다면 동기화).

### 11. TEMPLATE.md hostname 표 status 갱신

현재 표 행:
```
| `scm.local` | scm-platform | hostname routing from bootstrap |
```

본 부트스트랩 후 status 는 그대로 두거나 `hostname routing` 으로 변경 (실 활성화 시점은 첫 service 가동되는 TASK-SCM-BE-001 이므로 본 task 에서는 status 유지가 정확함).

### 12. `tasks/INDEX.md` (root) ready 리스트 갱신

본 task spec 파일의 ready 등재 1줄 추가.

## Out of Scope

- **첫 service skeleton 생성** — `gateway-service` / `procurement-service` Spring Boot 모듈 생성, contract 작성, Hexagonal 디렉토리, Dockerfile 등 → 후속 **TASK-SCM-BE-001**.
- **GAP V0013 Flyway seed** — `scm` tenant 등록 + `scm-platform-user-flow-client` / `scm-platform-internal-services-client` 등 OIDC client 시드 → 후속 **TASK-MONO-XXX** (별도 spec PR).
- **GAP tenant 사전 등록 API 호출** — V0013 seed 적용 시점에 자동 처리.
- **Frontend** — scm v1 은 backend 전용. frontend 가 필요해지면 TASK-SCM-FE-XXX 신규.
- **`scripts/sync-portfolio.sh` PROJECT_REMOTES 등록** — 첫 v1 publish 시점에 별도 task. 부트스트랩 단계에서는 monorepo 내부 검증만.
- **타 프로젝트와의 통신 계약** — wms-platform 의 inventory event 를 scm-platform 이 구독할지 여부 등은 v2 통합 task.
- **CI 작업 분류 등록** — `.github/workflows/ci.yml` 의 build-and-test gradle 리스트에 scm 모듈 추가는 첫 service skeleton 등장 시점.

---

# Acceptance Criteria

## 디렉토리·파일 구조

1. `projects/scm-platform/` 아래 12 path 모두 존재. 빈 디렉토리는 `.gitkeep` 으로 보존.
2. `find projects/scm-platform -type f -name PROJECT.md` 가 1 hit.
3. `find projects/scm-platform/tasks -mindepth 1 -maxdepth 1 -type d` 가 6 디렉토리 (backlog/ready/in-progress/review/done/archive) 출력.

## PROJECT.md 검증

4. frontmatter 의 `domain: scm` 가 `rules/taxonomy.md` § Domains 의 scm 정의와 일치.
5. `traits` 3개 (`transactional`, `integration-heavy`, `batch-heavy`) 가 모두 `rules/taxonomy.md` § Traits 에 등재된 값.
6. `service_types` 모두 `platform/service-types/INDEX.md` 카탈로그 멤버.
7. Service Map v1 / v2 분리 명시.
8. GAP IdP Integration 섹션 존재 (TEMPLATE.md 패턴 따름).

## Rules 정합성

9. `rules/domains/scm.md` 신설. 9 섹션 (Scope / Bounded Contexts / Ubiquitous Language / Standard Error Codes / Integration Boundaries / Mandatory Rules / Forbidden Patterns / Required Artifacts / Checklist) 모두 존재.
10. `rules/domains/scm.md` 에 구체 service 명 (`procurement-service` 등) 직접 노출 0 hit. (`grep -E "(procurement|settlement|logistics)-service" rules/domains/scm.md` empty.)
11. `rules/README.md` 의 도메인 카운트 표기와 일치.
12. `.claude/config/activation-rules.md` 의 scm 섹션 → `*(file to be created when ...)*` 가 `[`rules/domains/scm.md`](../../rules/domains/scm.md)` 로 갱신.

## docker-compose 검증

13. `docker compose --project-directory projects/scm-platform config 2>&1 | grep -E "published"` 출력 empty (host port 노출 0).
14. `gateway-service` (활성화 시) 라벨 4종 모두 존재: `traefik.enable=true`, `traefik.docker.network=traefik-net`, `traefik.http.routers.scm-platform.rule=Host(\`scm.local\`)`, `traefik.http.routers.scm-platform.entrypoints=web`, `traefik.http.services.scm-platform.loadbalancer.server.port=8080`.
15. `traefik-net` external 선언 (`name: traefik-net`).
16. 부트스트랩 단계에서 `gateway-service` build context 가 아직 없는 점은 헤더 주석으로 명시.

## 루트 통합

17. 루트 `package.json` 에 `scm:up`, `scm:down`, `scm:ps`, `scm:logs`, `scm:docker` 5 스크립트 모두 존재.
18. `./gradlew projects` 실행 시 기존 4 프로젝트의 인식이 깨지지 않음 (regression 0).
19. `grep -rn "PORT_PREFIX" projects/scm-platform/ 2>/dev/null` empty (hostname routing 100%).

## Library 경계 검증

20. `grep -rE "(procurement|settlement|logistics|demand-planning)" platform/ rules/common.md rules/traits/ libs/ tasks/templates/ docs/guides/ 2>/dev/null` empty (구체 scm 서비스명이 라이브러리 layer 에 leak 되지 않음). `rules/domains/scm.md` 안의 bounded context 이름 (`Procurement`) 은 추상 도메인 용어이므로 허용.

## TEMPLATE.md 정합성

21. TEMPLATE.md hostname 표의 `scm.local` / `scm-platform` 행 status 가 현실 (부트스트랩 완료 / 첫 service 미가동) 을 정확히 반영.

## tasks/INDEX.md (root) 정합성

22. ready 리스트에서 본 task 가 1 항목으로 등재.
23. 본 PR 머지 후 ready → in-progress → review → done 흐름 진행 시 INDEX.md 가 정상 갱신될 수 있는 형식.

## PR Separation Rule

24. 본 spec PR 은 implementation 변경 없음 (구현은 본 task 의 impl PR 또는 후속 TASK-SCM-BE-001 분리).
25. spec PR 은 본 task 파일 + tasks/INDEX.md ready 등재 + (병행 가능) ADR 작성 PR 머지 conflict 만 resolve.

---

# Related Specs

- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) (ACCEPTED 2026-05-04) — Phase 4 catalyst 로서 scm 첫 도메인 채택. 본 task 가 ADR § 5 Migration/Implementation Plan 의 직접 후속.
- `TEMPLATE.md` § "Starting a New Project in the Monorepo (Phase 2+)" — Option A Greenfield Step 1~12
- `TEMPLATE.md` § "GAP IdP Integration Pattern (New Projects)" Step 1~5 (후속 task 참조용)
- `TEMPLATE.md` § "Local Network Convention" — hostname allocation 표
- `CLAUDE.md` § "Project Classification" / "Hard Stop Rules" / "Local Network Convention"
- `rules/taxonomy.md` § scm domain 정의
- `rules/README.md` — On-Demand Rule Policy
- `.claude/config/domains.md` — scm 등록 확인
- `.claude/config/activation-rules.md` — scm 섹션 (detailed rule link 갱신)
- `platform/service-types/INDEX.md` — 카탈로그 검증
- `tasks/done/TASK-MONO-037-template-md-bootstrap-dryrun.md` — TEMPLATE.md 정합성 사전 catch
- `tasks/done/TASK-MONO-038-monorepo-workflow-guide.md` — 워크플로 참조
- 참고: `projects/fan-platform/PROJECT.md`, `projects/wms-platform/PROJECT.md`, `projects/fan-platform/tasks/INDEX.md`, `projects/fan-platform/docker-compose.yml`

# Related Contracts

본 task 자체는 contract 변경 없음. 후속 TASK-SCM-BE-001 가 첫 contract (`projects/scm-platform/specs/contracts/http/<gateway>-public-routes.md`) 도입 예정.

---

# Edge Cases

1. **다른 세션의 ADR-MONO-002 작성 (TASK-MONO-041) 과 INDEX.md ready 리스트 동시 추가** → mechanical line-level conflict, 머지 시 1줄 resolve 로 양 task 모두 ready 등재.
2. **`rules/domains/scm.md` 와 `rules/domains/wms.md` 의 의미적 중복** → scm 의 cross-functional 흐름 (조달 → 운송 → 정산) vs wms 의 단일 창고 운영 (입고 → 적치 → 피킹 → 출하) 으로 명확히 분리. 두 도메인이 한 프로젝트 안에서 결합되는 경우 (예: scm-platform 안에 mini wms 필요) 는 본 task 범위 밖.
3. **traits 선택 — `data-intensive` 미포함**: scm 은 실제 운영에서 TB+ 데이터 가능하나, 포트폴리오 규모 v1 에서는 batch-heavy 만으로 충분. 향후 데이터 볼륨이 핵심 제약이 되면 PROJECT.md 의 traits 갱신 (CLAUDE.md Project Classification 절차).
4. **traits 선택 — `multi-tenant` 미포함**: GAP 의 tenant_id=scm claim 은 사용하나, scm-platform 내부에서 다수 tenant 를 지원하는 SaaS 가 아님 (단일 조직의 공급망). 따라서 multi-tenant trait 미선언, 단 token validation 시 `tenant_id=scm` 만 허용 (gateway 책임).
5. **gateway-service docker-compose v1 placeholder 처리 방식**: build context 부재 시 (a) service 자체를 헤더 주석 처리, (b) `image:` 만 두고 `build:` 코멘트 처리. fan-platform v1 초기 커밋은 (a) 패턴이 깨끗 — 본 task 도 (a) 채택.
6. **`./gradlew projects` 실행 시 빈 apps 디렉토리 처리**: settings.gradle include 추가가 없으므로 gradle 입장에서 scm-platform 은 unknown — 정상 동작. 첫 service skeleton 시점에 include 추가.
7. **루트 `package.json` 의 기존 fan-platform / wms / ecommerce / gap 스크립트 순서 보존**: scm 항목은 알파벳 순 또는 추가 순으로 배치 (정해진 컨벤션 없음 — fan-platform 패턴 답습).

---

# Failure Scenarios

## A. taxonomy/dispatch/activation 정합성 깨진 상태에서 PROJECT.md 작성 시도

`rules/taxonomy.md` 와 `.claude/config/{domains.md, activation-rules.md}` 중 한 곳에서 `scm` 정의가 누락 / 모순이면 CLAUDE.md Hard Stop. **사전 검증** 결과 (TASK-MONO-029 audit 시점) 3 카탈로그 모두 등록 완료, 본 task 시작 전 마지막 grep 으로 재확인.

## B. PROJECT.md frontmatter trait 미등록 값 사용

`transactional` / `integration-heavy` / `batch-heavy` 중 하나라도 `rules/taxonomy.md` 에 없으면 Hard Stop. 사전 확인 — 셋 모두 § Traits 에 등재.

## C. Library 경계 위반

`rules/domains/scm.md` 또는 `platform/` / `libs/` / `tasks/templates/` / `docs/guides/` 어디든 구체 service 명 (`procurement-service` 등) 또는 scm 전용 API path 가 leak 되면 CLAUDE.md Hard Stop. 본 task 에서는 `rules/domains/scm.md` 작성 시 추상 placeholder 사용, 다른 shared layer 는 변경 없음.

## D. PR Separation Rule 위반 — spec + impl 동일 PR

본 spec PR 에 service 코드 / docker-compose 활성화 / GAP V0013 seed 가 함께 들어가면 ready 단계가 main 에서 사라져 외부 관찰자 (다른 AI 세션, 리뷰어) 가 ready 큐를 못 읽음. 본 task 는 spec PR 만 — 후속 impl PR 분리.

## E. TEMPLATE.md hostname 표와 docker-compose Traefik label 의 hostname 불일치

`scm.local` 가 한 곳이라도 `scm-platform.local` 등 다른 형식으로 적히면 dev hosts 파일 등록과 어긋나 routing 실패. 본 task 는 TEMPLATE.md / docker-compose / .env.example / README 모두 `scm.local` 로 통일.

## F. 다른 세션의 INDEX.md 변경과 머지 conflict

ADR-MONO-002 task (TASK-MONO-041) 가 같은 ready 리스트를 수정 → 머지 시 1줄 resolve. 양 task 모두 등재되도록 conflict resolution.

## G. 기존 프로젝트 빌드 / dev 흐름 깨짐

`settings.gradle` 변경 없음 + `package.json` 은 추가만 → 기존 4 프로젝트 영향 0 예상. 그래도 검증: `./gradlew projects` 출력에 기존 프로젝트 모두 보이는지 확인.

---

# Notes

- **Recommended impl model**: Opus 4.7 (1M context). 도메인 rule 파일 신설 + 다중 파일 cross-reference + Library 경계 의식적 보존이 필요하므로 단순 fix 아님.
- **분석=Opus 4.7 / 구현 권장=Opus** — 본 task 는 cross-cutting 부트스트랩 으로 다중 파일을 동시에 일관성 있게 작성해야 하며, fan-platform / wms 패턴을 새 도메인으로 번역하는 판단이 들어감.
- **후속 candidate** (별도 spec PR):
  - **TASK-SCM-BE-001** (project-level): 첫 service skeleton — gateway-service + procurement-service Spring Boot 모듈, Hexagonal 디렉토리, 첫 contract, settings.gradle include 갱신.
  - **TASK-MONO-0XX** (root-level): GAP V0013 Flyway seed — `scm` tenant + `scm-platform-user-flow-client` / `scm-platform-internal-services-client` 등록.
  - **TASK-SCM-INT-001**: scm ↔ wms-platform inventory snapshot subscription (cross-project event 통합).
- **dependency 표현**: 본 task 의 `선행` = TASK-MONO-041 / ADR-MONO-002 (이미 머지됨, PR #182~#184). 후속 (TASK-SCM-BE-001 / GAP V0013 seed task) 의 `선행` 으로 본 task 가 거론될 예정.
- **Phase 4 의의**: ADR-MONO-002 D3 가 deferred 인 이유 — 본 부트스트랩 종결 + 라이브러리 churn 안정 평가 후 ADR-MONO-003 (Template 레포 실제 추출) 가 발행될 예정. 본 task 의 impl PR 은 Phase 4 trigger 의 실 데이터 수집 의의도 가진다.
