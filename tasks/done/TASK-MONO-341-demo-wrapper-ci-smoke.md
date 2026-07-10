# Task ID

TASK-MONO-341

# Title

CI regression guard for the integrated demo wrapper (`infra/demo/`)

# Status

done

# Owner

devops

# Task Tags

- deploy
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-336 이 도입한 통합 데모 래퍼(`infra/demo/demo-up.sh`)가 앞으로 **조용히 깨지지 않도록** CI 회귀 방어를 붙인다.

래퍼의 정당성은 세 가지 불변식에 의존한다 — 이 중 하나라도 무너지면 데모가 부팅되지 않거나 일부 도메인이 소리없이 사라진다:

1. 각 프로젝트 compose 가 단독으로 렌더된다.
2. `container_name` 이 9개 compose 전역에서 **유일**하다 (docker 는 중복 container_name 을 거부).
3. host `ports:` 가 전역에서 충돌하지 않는다.

여기에 **드리프트 가드**를 하나 더 추가한다: 신규 프로젝트가 추가됐는데 래퍼의 프로젝트 맵에 등록되지 않으면(=데모에서 누락) CI 가 실패해야 한다. (선례: `nightly-e2e.yml` 서비스추가 드리프트.)

완료 시: `infra/demo/**`, `infra/traefik/**`, `projects/*/docker-compose.yml` 변경 PR 에서 `demo-wrapper-smoke` 잡이 위 불변식 + 실기동 증명을 자동 검증한다.

---

# Scope

## In Scope

- `infra/demo/projects.sh` **신설** — 프로젝트 슬러그→compose 경로 맵 + `FULL`/`CORE` 배열의 **단일 출처**
- `demo-up.sh` / `demo-down.sh` 가 `projects.sh` 를 source 하도록 리팩터 (행동 불변, 맵 중복 제거)
- `infra/demo/verify-demo-wrapper.sh` **신설** — 정적 불변식 검증 + `--live` 실기동 증명
  - (a) 9개 compose `docker compose config -q` 렌더
  - (b) `container_name` 전역 유일성
  - (c) host `ports:` 전역 무충돌
  - (d) **커버리지 드리프트 가드** — 디스크의 모든 `projects/*/docker-compose.yml` 이 맵에 등록돼 있는가
  - (e) `--live`: 서로 다른 프로젝트의 **같은 서비스 키**(`redis`)를 별도 `-p` 로 띄워 둘 다 healthy 확인 후 teardown
- `.github/workflows/ci.yml`
  - `changes` 잡에 **순수-positive** `demo-wrapper` 필터 추가 (negation 금지 — MONO-074/075 quirk)
  - `code-changed` 와 outputs 레이어에서 AND 합성 (md-only PR 은 skip)
  - `demo-wrapper-smoke` 잡 추가 (`needs: changes`, `if:` 게이팅, `--live` 포함)

## Out of Scope

- `full`(41 JVM) 실기동 스모크 — GH 러너(16GB) 물리적 불가. **EC2 `m6i.4xlarge` 권위** (MONO-336 이월 항목)
- `demo-core`(≈26 서비스) 실기동 — 러너 리소스 초과
- 기존 `projects/*/docker-compose.yml` / `infra/traefik/` 수정 (byte-unchanged 불변식 유지)
- `nightly-e2e.yml` / `federation-hardening-e2e.yml` 변경 (이 잡은 `ci.yml` 전용)
- AWS Terraform / AMI / start-stop (scratchpad PoC `ondemand-demo/`)

---

# Acceptance Criteria

- [ ] `bash infra/demo/verify-demo-wrapper.sh` 가 로컬에서 PASS (정적 4종)
- [ ] `bash infra/demo/verify-demo-wrapper.sh --live` 가 두 `redis` 공존 healthy 를 증명하고 teardown
- [ ] `demo-up.sh` / `demo-down.sh` 가 `projects.sh` 를 source 하며 **행동 불변** (`bash -n` + 맵 동일)
- [ ] 커버리지 가드가 실제로 잡는다 — 맵에서 프로젝트 1개를 제거하면 스크립트가 **FAIL**
- [ ] `ci.yml` 의 `demo-wrapper` 필터는 negation 패턴 0개 (순수-positive)
- [ ] `demo-wrapper-smoke` 잡이 이 PR 에서 실행되어 GREEN
- [ ] md-only 변경(`infra/demo/README.md`)만 있는 PR 에서는 잡이 skip 된다

---

# Related Specs

> Monorepo-level task. Read root `tasks/INDEX.md` § "When to Use Root vs Project Tasks" first.

- `tasks/done/TASK-MONO-336-integrated-demo-compose.md` (래퍼 도입, 설계 근거)
- `infra/demo/README.md` (래퍼 근거·사용법)
- `TEMPLATE.md` § Local Network Convention
- `.github/workflows/ci.yml` § `changes` (dorny/paths-filter 규약)

# Related Skills

- `.claude/skills/` — devops / CI 관련

---

# Related Contracts

- 없음 (CI + 인프라 스크립트, API/event 계약 변경 없음)

---

# Target Service

- N/A (cross-project infra) — 산출물: `infra/demo/`, `.github/workflows/ci.yml`

---

# Architecture

- `projects.sh` = 맵 단일 출처. `demo-up.sh`/`demo-down.sh`/`verify-demo-wrapper.sh` 가 공통 source → 맵 드리프트 원천 차단.
- 검증은 **정적 우선**(config 렌더 + 이름/포트 유일성 + 커버리지). 실기동은 **최소 증명**(2개 redis)만 — 러너 리소스 한계 내에서 설계의 유일한 비자명 주장만 확인.
- jq 미사용 (러너 외 환경 호환) — `docker compose config` YAML 을 grep/awk 로 파싱.

---

# Implementation Notes

- `dorny/paths-filter` **negation 금지**. `demo-wrapper` 는 `infra/demo/**`, `infra/traefik/**`, `projects/*/docker-compose.yml` 순수-positive. `code-changed`(`.sh`/`.yml` 포함) 와 outputs 레이어 AND → README-only PR skip.
- `container_name` 유일성이 `-p` 설계의 사전조건이다. 현재는 전부 프로젝트-슬러그 프리픽스라 충족.
- 실기동 증명은 `scm` + `fan` 의 `redis`(둘 다 `redis:7-alpine`, 경량) 사용. `REDIS_PASSWORD` 등 `${VAR}` 는 스모크용 더미 주입.
- teardown 은 `trap` 으로 보장 (실패해도 컨테이너 잔류 금지).

---

# Edge Cases

- `${VAR}` 미설정 → `docker compose config` 는 빈 문자열 + 경고(에러 아님). 렌더 검증에는 무해.
- `projects/platform-console/docker-compose.yml` 은 private network 없음 → 커버리지 가드가 network 유무를 전제하면 안 됨.
- `finance`/`erp` 의 `gateway-service` 는 주석 처리 상태 → 활성화 시 `gateway-service` 키가 `scm`/`fan` 과 겹치지만 **별도 `-p` 라 무해**. container_name 만 유일하면 됨.
- 신규 프로젝트가 `docker-compose.yml` 없이 추가될 수 있음 → 커버리지 가드는 파일 존재하는 프로젝트만 대상.

---

# Failure Scenarios

- 신규 프로젝트 추가 후 래퍼 맵 미등록 → 데모에서 조용히 누락 → **커버리지 가드가 FAIL**
- 누군가 `container_name` 프리픽스를 제거 → 두 프로젝트가 같은 컨테이너명 → docker 거부 → **유일성 가드가 FAIL**
- 누군가 래퍼를 `include:` 로 되돌림 → 중복 키 침묵 병합 → 실기동 증명(2 redis) 이 **1개만 뜨며 FAIL**
- host `ports:` 신규 노출이 traefik 80/443/8080 또는 jaeger 16686 과 충돌 → **포트 가드가 FAIL**
- 러너 리소스 초과 → 스모크가 경량(2 redis)이라 해당 없음

---

# Test Requirements

- `bash -n` (3개 스크립트)
- `verify-demo-wrapper.sh` 정적 4종 로컬 PASS
- `verify-demo-wrapper.sh --live` 로컬 PASS (2 redis 공존 → teardown)
- 네거티브 테스트: 맵에서 프로젝트 제거 → 커버리지 가드 FAIL 확인
- CI: `demo-wrapper-smoke` 잡 GREEN

---

# Definition of Done

- [ ] `projects.sh` / `verify-demo-wrapper.sh` 추가, `demo-up.sh`/`demo-down.sh` 리팩터
- [ ] `ci.yml` 필터 + 잡 추가 (negation 0)
- [ ] 로컬 정적 + `--live` 검증 통과, 네거티브 테스트 확인
- [ ] CI `demo-wrapper-smoke` GREEN
- [ ] Ready for review
