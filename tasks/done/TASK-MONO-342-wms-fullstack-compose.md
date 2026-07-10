# Task ID

TASK-MONO-342

# Title

Add the missing wms-platform full-stack compose overlay (`docker-compose.e2e.yml`)

# Status

done

# Owner

devops

# Task Tags

- deploy
- code

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

`wms-platform` 의 애플리케이션 서비스를 **컨테이너로 기동할 수 있는 수단이 저장소에 없다**. 이 결손을 메운다.

저장소에는 두 패턴이 공존한다:

- **패턴 1** — base `docker-compose.yml` = 인프라 전용, `docker-compose.e2e.yml` = 풀스택 하네스. (`iam-platform`, `platform-console`)
- **패턴 2** — base 가 앱까지 전부 포함. (`scm`, `fan`, `finance`, `erp`, `ecommerce`)

`wms-platform` 은 패턴 1 의 base 만 갖고 **풀스택 반쪽이 없다**. 그 결과 wms 앱 7개는 `bootRun` 또는 `tests/fulfillment-demo/`·`tests/federation-hardening-e2e/`(각각 2개씩만 정의)로만 뜬다. 통합 데모(`infra/demo`)가 wms 를 켜도 **DB 만 뜨고 앱은 하나도 뜨지 않는다** — TASK-MONO-336 의 `full` 프로파일이 조용히 불완전했던 원인.

완료 시: `projects/wms-platform/docker-compose.yml` + `docker-compose.e2e.yml` 로 wms 7개 앱이 컨테이너 기동된다.

---

# Scope

## In Scope

- `projects/wms-platform/docker-compose.e2e.yml` 신설 — **additive 오버레이**
  - 앱 7개: `gateway-service`(8080) `master-service`(8081) `inbound-service`(8082) `inventory-service`(8083) `outbound-service`(8084) `notification-service`(8085) `admin-service`(8086)
  - base 의 `postgres`(init.sh 가 6 DB 생성) / `redis` / `kafka` 재사용 — 신규 백킹 서비스 없음
  - `expose:` 만 (Local Network Convention). `gateway-service` 만 Traefik `wms.local` 라우팅
  - env 는 각 서비스 `application.yml` 의 `${VAR}` 계약에서 도출 (추측 금지)
- `base docker-compose.yml` **byte-unchanged** 유지

## Out of Scope

- `infra/demo` 통합 배선 (`projects.sh` 다중 `-f`, federation env) → 후속 task
- MONO-341 CI 가드에 "각 데모 프로젝트가 앱 서비스 ≥1 기여" 검사 추가 → 후속 task
- 7개 앱 전체 동시 실기동 스모크 — 개발 호스트 Docker VM 11.68 GiB 로는 불가. EC2/CI 권위
- iam 은 이미 `docker-compose.e2e.yml` 을 가지므로 무변경

---

# Acceptance Criteria

- [x] `docker compose -f docker-compose.yml -f docker-compose.e2e.yml config -q` 렌더 성공 (17 서비스)
- [x] `projects/wms-platform/docker-compose.yml` git diff == 0 (byte-unchanged)
- [x] MONO-341 가드 통과 — `container_name` 전역 유일, host port 무충돌
- [x] **실기동 검증**: `postgres + redis + kafka + master-service` 기동 → `wms-master-service` **healthy**, `/actuator/health` = `{"status":"UP"}`, Flyway 8 마이그레이션 성공(master_db 테이블 10개)
- [ ] (EC2/CI 권위) 7개 앱 전체 동시 기동 healthcheck — **이월**: 개발 호스트 Docker VM 11.68 GiB 로 물리적 불가

---

# Related Specs

> Monorepo-level task. Read root `tasks/INDEX.md` § "When to Use Root vs Project Tasks" first.

- `projects/iam-platform/docker-compose.e2e.yml` (패턴 1 의 준거 구현)
- `projects/wms-platform/docker-compose.yml` (base — 인프라 전용, 헤더에 의도 명시)
- `projects/wms-platform/docker/postgres/init.sh` (6 DB + role 의 authoritative 정의)
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` (master/admin env 참조)
- `TEMPLATE.md` § Local Network Convention, `docs/adr/ADR-MONO-001-port-prefix-scaling.md`
- `tasks/done/TASK-MONO-336-integrated-demo-compose.md` (이 결손이 드러난 경위)

# Related Skills

- `.claude/skills/` — devops / deploy

---

# Related Contracts

- 없음 (인프라 조립, API/event 계약 변경 없음)

---

# Target Service

- `wms-platform` 전체 (앱 7개) — 산출물: `projects/wms-platform/docker-compose.e2e.yml`

---

# Architecture

- **Additive 오버레이**. base 의 서비스 키를 하나도 재정의하지 않으므로 `-f base -f e2e` 병합에 충돌이 없다 (단일 compose 프로젝트 내부 오버레이 = 정상 용법; 위험한 것은 *프로젝트 간* 병합이다 — MONO-336).
- 앱 → DB 매핑은 `init.sh` 의 role/db 쌍을 그대로 따른다 (`master/master_db`, `inbound/inbound_db`, …). gateway 는 stateless 라 DB 없음.
- OIDC 는 env 로 주입 가능하게 두고 기본값은 통합 데모 토폴로지(iam 이 옆에서 기동)를 가정.

---

# Implementation Notes

- **선행 빌드 필수**: 모든 wms Dockerfile 은 `COPY build/libs/<svc>.jar` 이다(도커 안에서 컴파일하지 않음). 따라서 `docker compose build` 전에
  `./gradlew :projects:wms-platform:apps:<svc>:bootJar` 가 각 서비스마다 선행돼야 하고, 공유 런타임 베이스 `monorepo/java-service-base:v1` 이미지도 존재해야 한다 (ADR-MONO-041).
- **redis 비밀번호**: base 의 `redis` 는 `${REDIS_PASSWORD}` 로 보호되는데 wms 앱들은 `REDIS_PASSWORD` env 를 읽지 않는다. 이 오버레이는 `REDIS_PASSWORD` 를 비워둔 상태로 기동해야 한다.
- 빌드 컨텍스트는 **서비스 디렉터리**(`apps/<svc>`) — iam 의 Dockerfile 은 프로젝트 루트 컨텍스트를 쓰므로 패턴이 다르다. wms Dockerfile 의 `COPY build/libs/...` 를 따를 것.
- 메모리 실측: 갓 기동한 `master-service` RSS **762 MiB** (오래 돌아간 fed-e2e 서비스 평균 ~390 MiB). 힙 상한(`-Xmx`) 미설정 시 컨테이너 메모리의 1/4 까지 팽창하므로, 다수 동시 기동 시 `JAVA_TOOL_OPTIONS=-Xmx…` 캡이 사이징에 결정적이다.

---

# Edge Cases

- `gateway-service` 는 `init.sh` 에 DB 가 없다(stateless) — DB env 를 주면 안 된다.
- `notification-service` 는 event-consumer 라 OIDC 표면이 없다 — OIDC env 미주입.
- `outbound-service` 의 TMS 는 post-commit 외부 스텁(ADR-MONO-005 category D) — 스텁 URL 로 degrade 허용.
- base 를 단독으로 `up` 하면 여전히 인프라만 뜬다(기존 bootRun 워크플로 무손상).

---

# Failure Scenarios

- jar 미빌드 상태로 `--build` → `COPY build/libs/...` 실패. (Implementation Notes 의 선행 빌드)
- `REDIS_PASSWORD` 가 설정된 채 기동 → 앱이 redis 인증 실패.
- `container_name` 이 기존 9개 compose 와 충돌 → MONO-341 가드가 CI 에서 FAIL.
- 7개 동시 기동 시 힙 미상한 → 호스트 OOM. `-Xmx` 캡 또는 큰 인스턴스 필요.

---

# Test Requirements

- `docker compose config -q` (base + overlay) 렌더
- `git diff --exit-code projects/wms-platform/docker-compose.yml` == 0
- `infra/demo/verify-demo-wrapper.sh` (MONO-341 가드) 통과
- 실기동: `master-service` healthy + `/actuator/health` UP + Flyway 성공 (완료)
- (EC2/CI) 7개 앱 전체 동시 기동

---

# Definition of Done

- [x] `docker-compose.e2e.yml` 추가, base byte-unchanged
- [x] 렌더 + MONO-341 가드 + master-service 실기동 healthy 검증
- [ ] (EC2/CI) 전체 7앱 스모크 — **이월**(호스트 물리 한계, EC2 `m6i.2xlarge` 권위)
- [x] 후속 task 티켓화: `infra/demo` 통합 배선 + CI "앱 서비스 ≥1" 가드 → TASK-MONO-344 (동일 PR #2383 에서 함께 구현·머지)
- [x] Ready for review
