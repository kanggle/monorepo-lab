# Task ID

TASK-MONO-028

# Title

ecommerce standalone v1 freeze policy — sync-portfolio.sh 보강 + GAP cutover follow-up cleanup

# Status

ready

# Owner

backend

# Task Tags

- chore
- portfolio

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

ecommerce GAP cutover 시리즈 (PR #145 / #148 / #150) 가 main 에 머지된 결과, monorepo 의 ecommerce 는 GAP OIDC consumer 로 완전 전환됐고 자체 auth-service 컴포넌트가 빌드 / 배포 / 컨테이너 / 환경변수 / 스펙 차원에서 제거됐다.

그러나 standalone portfolio v1 (`kanggle/ecommerce-microservices-platform`) 은 자체 auth-service 시연을 보존하는 게 의도이다 (memory: project_ecommerce_import_plan, project_portfolio_submission_strategy). `scripts/sync-portfolio.sh` 의 `PROJECT_EXCLUDE_PATHS` 가 TASK-FE-067 (PR #148) 의 frontend GAP 변경분만 부분적으로 제외하고 있고, BE-132 (PR #150) 의 backend 제거분은 전혀 제외되지 않은 상태다. 다음 sync 실행 시 standalone v1 의 docker-compose / k8s / settings.gradle / .env / spec / gateway application.yml 이 모두 monorepo 상태로 덮어쓰여지면서 standalone 의 auth-service 시연이 깨진다.

본 task 는 ecommerce standalone v1 을 "GAP cutover 이전 시점에서 frozen" 으로 명시적으로 마킹하고, sync-portfolio.sh 가 GAP cutover 관련 path 전부를 standalone sync 에서 제외하도록 보강한다. 또한 BE-132 review 에서 deferred 된 secondary cleanup (환경변수 잔재 grep, done task 의 깨진 spec 경로) 도 같이 처리한다.

---

# Scope

## In Scope

### 1. `scripts/sync-portfolio.sh` PROJECT_EXCLUDE_PATHS 보강

현재 `PROJECT_EXCLUDE_PATHS["ecommerce-microservices-platform"]` 에는 FE-067 의 frontend NextAuth 관련 path 만 포함. BE-132 가 변경한 path 가 standalone 으로 sync 되지 않도록 다음 추가:

| 영역 | 추가 path |
|---|---|
| docker-compose | `docker-compose.yml`, `docker-compose.ci.yml`, `docker-compose.bootrun.yml` (auth-service 블록 제거가 sync 되면 standalone 에서 자체 auth-service 가 사라짐) |
| .env | `.env.example` (project root — JWT_SECRET / ADMIN_INITIAL_PASSWORD 제거 sync 차단) |
| Gradle | (root `settings.gradle` 은 SHARED_PATHS 에 있어 무관 — 단 standalone 에서 자체 auth-service include 가 필요한 경우 별도 patch) |
| k8s | `k8s/` 통째 (services/auth-service-deprecated, network-policies/auth-service.yaml 삭제, secrets.yaml 변경, gateway env 변경 등 광범위) |
| gateway 코드 | `apps/gateway-service/src/main/resources/application.yml` (auth-service route 블록 제거 + GAP OIDC validator 설정 추가) |
| spec rename | `specs/services/auth-service-deprecated/`, `specs/services/auth-service/` (없어진 디렉토리지만 standalone 은 가지고 있음) |
| spec contracts | `specs/contracts/http/auth-api.md` (DEPRECATED 헤더 sync 차단), `specs/contracts/events/auth-events.md` (DEPRECATED 헤더 sync 차단) |
| spec features | `specs/features/authentication.md`, `specs/features/user-management.md` (DEPRECATED 표시 + striked-out sync 차단) |

`PROJECT_EXCLUDE_PATHS` 의 주석 (현재는 frontend 만 언급) 도 갱신 — standalone v1 이 GAP cutover 이전 frozen 상태임을 명시.

### 2. ecommerce monorepo 측 환경변수 잔재 grep & 제거

PR #150 의 code-reviewer Suggestion #8: task 본문 line 60 에 명시된 `AUTH_SECRET`, `AUTH_GOOGLE_CLIENT_ID`, `AUTH_GOOGLE_CLIENT_SECRET` 가 PR #150 의 변경분에 명시적으로 나타나지 않음. 다음 grep 으로 확인 + 잔재 시 제거:

- `AUTH_SECRET` — root `.env.example`, project `.env.example`, k8s manifests, docs 의 잔재
- `AUTH_GOOGLE_CLIENT_ID` / `AUTH_GOOGLE_CLIENT_SECRET` — 동일
- `ADMIN_INITIAL_PASSWORD` — auth-service 외 다른 위치 사용 여부

monorepo 에서만 처리; standalone 영향은 #1 의 `.env.example` exclusion 으로 차단.

### 3. tasks/done/ 의 깨진 spec 경로 (선택적)

PR #150 으로 `specs/services/auth-service/` → `specs/services/auth-service-deprecated/` 이동했는데, ecommerce 의 `tasks/done/` 안 다수 task 가 옛 경로를 참조 (e.g. `TASK-BE-001-auth-service-bootstrap.md`, `TASK-BE-114`, `TASK-BE-119` 등 auth-service 관련).

CLAUDE.md 룰: "Do not modify a task file after it moves to review or done". 이 룰과 정합되는 처리 정책:

- **옵션 a (방치)**: 룰 그대로 따름. done task 는 history snapshot — 당시 경로 기록은 유효.
- **옵션 b (한 번만 cleanup + 룰 예외 명시)**: 본 task 에서 경로 일괄 sed-rewrite + CLAUDE.md / `tasks/INDEX.md` 에 "spec rename 시 done task 경로 갱신은 예외 허용" 명시.
- **옵션 c (인덱스 redirect)**: `auth-service-deprecated/README.md` 에 "이전 경로: `auth-service/`" 명시 (이미 PR #150 fix commit 에서 처리됨) + done task 그대로 둠.

본 task 는 **옵션 a (방치) + c (이미 처리됨)** 를 명시적으로 선택. README redirect 가 있으므로 reader 는 자력 도달 가능. 별도 grep 결과를 task 결과 보고에 포함하여 영향 범위 가시화.

### 4. k8s prod gateway → GAP egress NetworkPolicy (deferred 명시)

PR #150 fix commit 의 PR comment 에서 언급 — `gateway-service-netpol` 의 egress 규칙에 GAP namespace 허용 추가가 prod k8s 에서 필요하나 portfolio dev 범위 밖. 본 task 에서는 spec 만 명시 + 별도 (TASK-MONO-029 candidate 또는 GAP 측 task) 로 deferred.

## Out of Scope

- ecommerce 사용자 데이터의 GAP DB 마이그레이션 (portfolio 범위 밖, production 운영 시 별도 데이터 엔지니어링 task)
- 다른 프로젝트 (wms / fan-platform) 의 standalone freeze 정책 — 두 프로젝트는 GAP cutover 가 v1 에 포함되어 있어 standalone 도 동일하게 GAP 통합 상태. 본 task 는 ecommerce standalone v1 한정.
- standalone v1 자체의 라이브 검증 — `pnpm sync-portfolio` 실제 실행 + standalone repo 에서 `docker compose up` 검증은 매뉴얼 단계로 본 PR 의 Acceptance Criteria 외.
- TASK-FE-068 (admin-dashboard SSR baseURL fallback) — 별도 task 로 분리 (PR #148 review 산출).

---

# Acceptance Criteria

- [ ] `scripts/sync-portfolio.sh` 의 `PROJECT_EXCLUDE_PATHS["ecommerce-microservices-platform"]` 가 위 표의 8개 path 카테고리 전부 포함.
- [ ] `PROJECT_EXCLUDE_PATHS` 인근 주석이 standalone v1 의 frozen 상태 (= GAP cutover 이전) 를 명시적으로 설명.
- [ ] `scripts/sync-portfolio.sh --dry-run ecommerce-microservices-platform` 실행 시 BE-132 변경 path 들이 모두 exclusion 단계에서 제외되는 게 dry-run 출력으로 확인.
- [ ] monorepo 의 `AUTH_SECRET` / `AUTH_GOOGLE_CLIENT_ID` / `AUTH_GOOGLE_CLIENT_SECRET` / `ADMIN_INITIAL_PASSWORD` grep 결과를 task 결과에 포함, 잔재 시 제거 commit.
- [ ] tasks/done/ 의 auth-service 경로 참조 grep 결과를 task 결과에 포함 (옵션 a + c 정책 명시).
- [ ] (deferred) k8s prod GAP egress 추가는 본 task 에서 처리하지 않음을 PR body / commit message 에 명시 + 별도 follow-up task ID candidate 표기.

---

# Related Specs

- `tasks/done/TASK-MONO-027-ecommerce-gap-integration.md`
- `projects/ecommerce-microservices-platform/tasks/done/TASK-FE-067-frontend-gap-oauth-cutover.md`
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-132-auth-service-deprecation.md`
- `scripts/sync-portfolio.sh` § PROJECT_EXCLUDE_PATHS (line 95~125)
- `projects/ecommerce-microservices-platform/k8s/services/auth-service-deprecated/README.md` (DO-NOT-APPLY 안내, PR #150 fix commit 산출)

---

# Related Skills

- 없음 (chore + script edit + grep)

---

# Target Service / Component

- `scripts/sync-portfolio.sh` (root)
- 잔재 grep 대상: monorepo 전체 (`projects/ecommerce-microservices-platform/` 우선)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. 본 task 는 코드 변경이 아니라 빌드/배포 스크립트 + 환경변수 정리 chore.

---

# Implementation Notes

- `sync-portfolio.sh` 의 exclusion 추가는 path prefix 매칭 형태. 디렉토리 단위 (`k8s/`) 를 통째 제외하면 standalone 의 k8s 디렉토리는 monorepo 의 k8s 변경을 영구히 받지 않음 — 이는 standalone v1 frozen 정책의 의도와 일치.
- `apps/gateway-service/src/main/resources/application.yml` exclusion 은 standalone 의 gateway 가 자체 auth-service JWT (HS256) 검증 모드를 유지함을 의미. monorepo 의 RS256/JWKS 변경과 분리.
- 잔재 grep 은 다음 패턴으로:
  - `grep -rn "AUTH_SECRET" projects/ecommerce-microservices-platform/`
  - `grep -rn "AUTH_GOOGLE_CLIENT" projects/ecommerce-microservices-platform/`
  - `grep -rn "ADMIN_INITIAL_PASSWORD" projects/ecommerce-microservices-platform/`
- 결과 grep 출력은 PR body 에 포함하여 reviewer 가 영향 범위를 즉시 파악할 수 있게.

---

# Edge Cases

- **standalone repo 가 monorepo 의 새 service 추가 (예: fan-platform e2e style) 를 받아야 하는 경우**: exclusion 이 너무 광범위하면 standalone 이 monorepo 발전을 따라가지 못함. 본 task 는 `auth-service` 관련 path 만 exclusion — 다른 경로 (apps/product-service/, apps/order-service/ 등) 는 sync 정상.
- **gateway application.yml 의 일부만 sync 하고 싶은 경우**: file-level exclusion 은 all-or-nothing. 부분 sync 가 필요하면 sync 후 standalone 에 별도 patch commit. 본 task 는 단순 exclusion 만.
- **`AUTH_SECRET` 가 root .env.example 에는 없지만 projects/ecommerce-microservices-platform/.env.example 에는 있을 가능성**: 양쪽 다 grep.
- **standalone v1 의 README / portfolio.md 가 monorepo 의 GAP 통합 README 변경으로 덮어쓰여질 가능성**: standalone README 는 이미 별도. project 의 README.md 는 SHARED_PATHS 에 없으므로 sync 됨 — exclusion 후보로 검토.

---

# Failure Scenarios

- **exclusion path 오타**: filter-repo --invert-paths 가 빈 결과 → 이 path 가 sync 됨. 검증: dry-run 출력 확인.
- **exclusion 너무 광범위**: standalone 이 monorepo 발전을 못 따라감. 본 task 는 ecommerce-only / auth-service-only 한정.
- **sync 실행 후 standalone 깨짐**: dry-run 으로 사전 검증. 실제 실행은 매뉴얼.
- **잔재 환경변수 cleanup 누락**: monorepo 빌드는 통과해도 docs / 다른 서비스의 .env.example 에 noise. PR review 단계 grep 으로 catch.

---

# Test Requirements

- bash dry-run: `./scripts/sync-portfolio.sh --dry-run ecommerce-microservices-platform` 로 exclusion 적용 확인.
- shellcheck (선택적): `shellcheck scripts/sync-portfolio.sh` 통과.
- grep 결과 attach: PR body 에 환경변수 grep 결과 + 깨진 spec 경로 grep 결과 포함.

---

# Definition of Done

- [ ] `scripts/sync-portfolio.sh` exclusion 보강 + 주석 갱신.
- [ ] dry-run 검증 통과.
- [ ] 환경변수 잔재 grep 결과 PR body 에 포함, 잔재 시 제거.
- [ ] tasks/done/ 의 auth-service 경로 참조 grep 결과 PR body 에 포함 (옵션 a + c 정책 명시).
- [ ] k8s prod GAP egress 는 별도 follow-up task candidate 로 명시.
- [ ] Ready for review.

---

# Prerequisites

- ✅ PR #145 (TASK-MONO-027) 머지 완료
- ✅ PR #148 (TASK-FE-067) 머지 완료
- ✅ PR #150 (TASK-BE-132) 머지 완료
