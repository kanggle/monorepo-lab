# Task ID

TASK-PC-BE-008

# Title

`projects/platform-console/docker-compose.e2e.yml` — iam build-context/volume 경로를 `../iam` → `../iam-platform` 로 정정. gap→iam-platform 리네임(#1149)이 남긴 잔재로 Platform Console 나이틀리 잡이 `unable to prepare context: path ".../projects/iam" not found` 로 실패(TASK-MONO-233 restore fix 후 노출된 다음 레이어).

# Status

review

# Owner

backend/infra (Opus 4.8 analysis + impl). 프로젝트 내부(`projects/platform-console/`). e2e docker-compose 단일 파일 5줄.

# Task Tags

- code
- deploy
- test

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

# Dependency Markers

- **선행/원인**: `refactor!: rename global-account-platform (gap) → iam-platform (iam)` (#1149) — IAM 프로젝트 디렉터리가 `iam` → `iam-platform` 으로 바뀌었으나 platform-console e2e compose 의 build context / volume 경로(`../iam`)가 미갱신.
- **직접 선행**: TASK-MONO-233 (nightly-e2e.yml jar-restore 경로 fix). 그 fix 로 restore 단계가 통과하면서 "Start docker compose stack" 단계로 진행 → 본 잔재가 노출됨. 두 task 는 동일 #1149 잔재의 서로 다른 레이어.

# Goal

Platform Console 나이틀리 잡의 "Start docker compose stack" 단계가 `docker compose -f docker-compose.e2e.yml up -d --build mysql redis auth-service account-service admin-service` 에서 auth-service 의 `build.context: ../iam` 를 해석하다 `unable to prepare context: path ".../projects/iam" not found` 로 exit 1 한다(IAM 디렉터리는 이제 `iam-platform`). compose 의 5개 `../iam` 참조(build context 3 + volume 2)를 `../iam-platform` 로 정정해 스택이 기동되고 Playwright 단계로 진행하도록 한다.

# Scope

## In Scope

- **`projects/platform-console/docker-compose.e2e.yml`** — `../iam` → `../iam-platform` 5곳:
  - L109 volume `../iam/docker/mysql/init.sh`
  - L175 `context: ../iam` (auth-service)
  - L192 volume `../iam/apps/auth-service/src/test/resources/keys`
  - L224 `context: ../iam` (account-service)
  - L255 `context: ../iam` (admin-service)

## Out of Scope

- finance-platform / console-bff / console-web build context(이미 정확) 불변.
- `projects/ecommerce-microservices-platform/docker-compose.iam-e2e.yml` 의 동일 `../iam` 잔재 — **다른 프로젝트 + 어떤 워크플로도 미참조(CI 미실행 latent)** → 별도 ecommerce task 후보(본 task 범위 아님).
- nightly-e2e.yml jar-restore 경로(TASK-MONO-233 에서 처리됨).
- web-store Frontend E2E 잡 실패(별건 — 본 task 무관).
- compose 의 서비스 구성·env·healthcheck·seed 로직 불변.

# Acceptance Criteria

- [ ] `docker-compose.e2e.yml` 의 `../iam`(non-platform) 참조 0건; 5곳 전부 `../iam-platform`.
- [ ] 정정된 5개 타깃 경로가 실재(`../iam-platform/docker/mysql/init.sh`, `../iam-platform/apps/{auth,account,admin}-service/Dockerfile`, `../iam-platform/apps/auth-service/src/test/resources/keys`).
- [ ] `docker compose -f docker-compose.e2e.yml config` 파싱 OK.
- [ ] 다음 nightly-e2e 의 "Platform Console E2E full-stack" 잡이 "Start docker compose stack" 단계를 통과(build context 해석 성공).

# Related Specs

- 없음 (e2e 인프라 compose, 계약 무관).

# Related Contracts

- 변경 없음.

# Target Service

- platform-console e2e 스택(auth/account/admin-service 이미지 빌드 컨텍스트). Service Type 변경 없음.

# Architecture

- platform-console e2e 는 IAM 3 서비스(auth/account/admin)를 IAM 프로젝트의 Dockerfile + 소스로 in-place 빌드한다(`build.context: ../iam-platform`, dockerfile=apps/<svc>/Dockerfile). mysql init 스크립트와 JWT 테스트 키도 IAM 트리에서 볼륨 마운트한다. #1149 가 디렉터리를 `iam-platform` 으로 옮겼으나 이 상대경로들이 옛 `../iam` 로 남아 build context 준비가 실패했다. nightly 전용(PR 미게이트) 잡이라 회귀가 누출.

# Edge Cases

- finance-platform context 는 풀네임(`../finance-platform/...`)이라 영향 없음.
- console-bff/console-web 는 `./apps/...`(platform-console 내부)라 무관.
- volume 마운트(init.sh / keys)도 같은 잔재 → 함께 정정하지 않으면 빌드는 통과해도 런타임에 init/키 마운트 실패. AC 가 5곳 전부 명시.

# Failure Scenarios

- build context 3곳만 고치고 volume 2곳 누락 → 컨테이너 기동 후 mysql init 미적용/JWT 키 부재로 후속 실패. AC 가 잔재 0건 명시.
- 경로를 존재하지 않는 이름으로 오타 정정 → 동일 context 에러. AC 가 타깃 경로 실재 + compose config 파싱으로 보증.

# Definition of Done

- [ ] `../iam` → `../iam-platform` 5곳 정정, 잔재 0
- [ ] 타깃 경로 실재 + `docker compose config` 파싱 OK
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
