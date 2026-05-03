# Task ID

TASK-FAN-INT-001

# Title

fan-platform v1 services E2E 시나리오 (gateway + community + artist 통합)

# Status

ready

# Owner

backend

# Task Tags

- code
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

# Goal

fan-platform v1 의 3 backend service (gateway / community / artist) 가 실제 인프라 위에서 end-to-end 로 동작함을 검증하는 통합 E2E 테스트 스위트를 구축한다.

각 서비스의 단위 / 슬라이스 / 통합 테스트는 이미 존재하지만, **3 서비스를 동시에 띄워 gateway 를 통해 cross-service 흐름을 검증** 하는 테스트가 없다 — 이는 portfolio 평가자가 로컬에서 `pnpm fan-platform:up` 으로 띄워 동일 시나리오를 재현할 수 있게 하는 demo path 의 핵심.

이 태스크 완료 후:

- `projects/fan-platform/tests/e2e/` 디렉토리에 JUnit 5 + Testcontainers 기반 e2e 테스트 모듈
- gateway + community + artist + postgres + redis + kafka + WireMock JWKS 를 컨테이너로 띄워 실제 트래픽 검증
- 3 핵심 시나리오:
  1. **artist directory + post publishing**: admin 이 artist 등록·발행 → fan 이 artist 팔로우 → fan 이 post 발행 → fan 이 자기 피드 조회 → outbox → Kafka 이벤트 확인
  2. **multi-tenant isolation**: `tenant_id=fan-platform` 토큰만 통과, `tenant_id=wms` 는 gateway 에서 403 차단
  3. **visibility tier**: PUBLIC post 는 누구나 조회 / MEMBERS_ONLY 는 멤버만 (membership-service 미존재 → MembershipChecker mock) / PREMIUM 은 v1 always-pass + log warn
- CI Linux 에서 실행 — 새로운 GitHub Actions job `fan-platform-e2e`
- 기존 wms `tests/e2e` (live-pair Testcontainers) 패턴 재사용 — 검증된 인프라 활용

이 테스트가 GREEN 이 되면 fan-platform v1 backend 가 portfolio demo 로 검증됨.

---

# Scope

## In Scope

### 1. e2e 모듈 셋업

- `projects/fan-platform/tests/e2e/` 디렉토리 + `build.gradle` (JUnit 5, Testcontainers, RestAssured/WebTestClient, Spring Kafka, libs:java-test-support)
- `settings.gradle` 루트에 `'projects:fan-platform:tests:e2e'` include 추가
- e2e module 은 production code 와 분리 — `:test` 가 아닌 `:e2eTest` 태스크로 실행

### 2. Testcontainers 인프라

- `FanPlatformE2ETestBase.java` (abstract base):
  - Postgres Testcontainer (init script 로 `fanplatform_community` + `fanplatform_artist` DB 생성)
  - Redis Testcontainer
  - Kafka Testcontainer (KRaft single-broker)
  - WireMock JWKS server (gateway / community / artist 가 동일 URI 사용)
  - **gateway-service / community-service / artist-service 를 GenericContainer 로 띄움** — 사전 빌드된 boot jar 또는 docker image 활용 (TASK-MONO-015 패턴 따름)
  - Docker network 공유 (`fan-platform-e2e-net`) — 서비스간 hostname 으로 접근
- `JwtTestHelper` — gateway-service 의 helper 재사용 또는 복제. fan/admin 토큰 발급.

### 3. 3 핵심 시나리오 테스트 클래스

- `ArtistAndPostFlowE2ETest` (happy path)
  - admin 토큰: POST /api/artists → 201 + outbox `artist.registered`
  - admin 토큰: PATCH /api/artists/{id}/status PUBLISHED → 200 + Kafka `artist.published.v1` 발행
  - fan 토큰: POST /api/community/follows {artistAccountId} → 201
  - fan 토큰: POST /api/community/posts → 201 + Kafka `community.post.published.v1`
  - fan2 토큰: POST /api/community/posts/{id}/reactions {LIKE} → 200
  - fan 토큰: GET /api/community/feed → 200 + 자기 post + 팔로우 한 artist 의 post 모두 포함
- `MultiTenantIsolationE2ETest`
  - `tenant_id=wms` 토큰으로 GET /api/community/posts → 403 TENANT_FORBIDDEN (gateway 차단)
  - 인증 없는 요청 → 401 UNAUTHORIZED
  - `tenant_id=fan-platform` 인증된 요청은 정상 통과
- `VisibilityTierE2ETest`
  - PUBLIC post: 누구나 GET → 200
  - MEMBERS_ONLY post: membership=true mock 일 때 200, false 일 때 403 MEMBERSHIP_REQUIRED
  - PREMIUM post: v1 항상 200 + log warn ("PREMIUM check skipped — membership-service not yet integrated")

### 4. CI 통합

- `.github/workflows/ci.yml` 에 새 job `fan-platform-e2e`:
  - `needs: [build-and-test, boot-jars]`
  - `boot-jars` 가 fan-platform 3 서비스의 boot jar 를 artifact 로 업로드
  - e2e job 이 artifact 다운로드 → docker build → docker compose up (또는 GenericContainer 활용) → e2eTest 실행
  - 패턴: ecommerce 의 `frontend-e2e` 또는 wms 의 `e2e-tests` job 모방
  - timeout 15-20분
  - artifact 업로드 (실패 시 로그 + screenshot)

### 5. spec 작성

- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md` (신규):
  - 3 시나리오의 전제 / 단계 / 기대 결과 / 검증 포인트 명시
  - 시나리오 매트릭스 (인증 / tenant / role / visibility) 표
  - membership-service 미존재 / search-service 미존재 등 v1 한계 명시

## Out of Scope

- Frontend e2e (Playwright) — TASK-FAN-FE-001 의 일부 (별도 task)
- membership-service / notification-service / admin-service E2E — v2
- 성능 / 부하 테스트 — 별도 task
- `community.reaction.added` / `community.comment.added` 이벤트 컨슈머 검증 — 컨슈머가 v1 미존재 (notification-service, search-service 가 v2)
- Cross-project E2E (예: GAP IdP 가 실제로 띄워진 상태) — 본 task 는 GAP 를 WireMock JWKS 로 mock
- artist account 본인이 본인 artist 프로필 수정 — v2 self-service

---

# Acceptance Criteria

- [ ] `./gradlew :projects:fan-platform:tests:e2e:e2eTest --no-daemon` 통과 (Docker 필요)
- [ ] `ArtistAndPostFlowE2ETest` 5단계 happy path 통과 — 마지막 GET /api/community/feed 가 fan 의 post + 팔로우 artist 의 post 둘 다 포함
- [ ] `MultiTenantIsolationE2ETest` — `tenant_id=wms` 토큰 시 gateway 단계에서 403 차단 (community/artist service 까지 트래픽 안 감)
- [ ] `VisibilityTierE2ETest` — PUBLIC ✓ / MEMBERS_ONLY membership=true 200, false 403 / PREMIUM 200 + 로그 warn
- [ ] outbox → Kafka 발행 검증: Awaitility 기반, 토픽 `artist.published.v1` + `community.post.published.v1` 메시지 envelope 검증
- [ ] CI Linux `fan-platform-e2e` job 추가 + 머지 후 GREEN
- [ ] `specs/integration/v1-e2e-scenarios.md` 작성

---

# Related Specs

- `projects/fan-platform/PROJECT.md` § Service Map (v1)
- `projects/fan-platform/specs/services/{gateway,community,artist}-service/architecture.md` (v1 3 서비스)
- `projects/fan-platform/specs/integration/gap-integration.md` (JWT claim contract)
- `projects/fan-platform/specs/contracts/http/{community-api,artist-api}.md`
- `projects/fan-platform/specs/contracts/events/{community-events,artist-events}.md`
- `platform/testing-strategy.md` § E2E
- `rules/traits/integration-heavy.md`
- `projects/wms-platform/tests/e2e/` (reference — Testcontainers live-pair 패턴)
- `projects/ecommerce-microservices-platform/.github/workflows/` (reference — frontend-e2e job)

# Related Skills

- `.claude/skills/testing/test-strategy/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/testing/e2e-test/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/testing/fixture-management/SKILL.md`
- `.claude/skills/infra/ci-cd/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`
- `projects/fan-platform/specs/contracts/events/community-events.md`
- `projects/fan-platform/specs/contracts/events/artist-events.md`

---

# Target Service / Component

- `projects/fan-platform/tests/e2e/` (신규 모듈)
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md` (신규)
- `settings.gradle` (루트, 새 module include)
- `.github/workflows/ci.yml` (새 job 추가)

---

# Architecture

`platform/architecture-decision-rule.md` 의 Service Type 은 적용 안 됨 (test 모듈). 단, 실행 순서:

1. Pre-build 3 service boot jars (`./gradlew :projects:fan-platform:apps:gateway-service:bootJar` 등)
2. Pre-build 3 docker images (`docker build` CLI — TASK-MONO-015 패턴)
3. Testcontainers 가 GenericContainer 로 사전 빌드된 image 를 띄움
4. RestAssured/WebTestClient 로 gateway 에 트래픽 send
5. Awaitility 로 비동기 outbox/Kafka 이벤트 검증

---

# Implementation Notes

- **wms `tests/e2e` 를 reference 로 복제** + 다음 변경:
  - 도메인 변경 (master/inventory/inbound/outbound → community/artist)
  - WireMock JWKS 서버 추가 (wms 는 자체 인증, 본 task 는 GAP IdP mock)
  - membership mock — v1 PREMIUM 정책 검증용
- ecommerce 의 `frontend-e2e` CI job 도 reference (boot jar artifact + docker build 패턴)
- `JwtTestHelper` 는 gateway-service 의 testsupport 에서 가져오는 게 가장 깔끔 — 단, e2e 모듈이 production module test 코드를 import 하면 안 되므로 별도 복제 또는 libs:java-test-support 로 추출 권장.
- e2e 모듈은 `:check` 에 포함되지 않음 — 별도 `:e2eTest` 태스크로만 실행. CI 에서만 돌리고 dev 머신에서는 옵션.
- fan-platform docker-compose.yml 을 직접 활용할 수도 있으나, CI 환경에서는 GenericContainer + 사전 빌드 image 가 더 빠르고 안정적 (TASK-MONO-015 결론).
- Test 환경 cleanup: 각 시나리오 클래스가 `@DirtiesContext` 또는 `@BeforeEach` 에서 DB clean (Postgres truncate). Kafka 토픽은 새 GROUP_ID 로 매번 새 consumer.

---

# Edge Cases

- **GAP IdP 가 실제로 띄워져 있지 않음 (WireMock JWKS 만)**: production 의 redirect/login flow 는 검증 안 함 — 본 task 는 토큰 검증 + tenant gate + 비즈니스 흐름만. login/redirect 는 frontend e2e 에서 (TASK-FAN-FE-001).
- **fan 이 자기 자신 팔로우 시도**: 422 SELF_FOLLOW_FORBIDDEN (community-events 에 명시) — 시나리오에 포함 권장.
- **archived artist 참조**: artist 가 ARCHIVED 된 후 fan 이 follow → community 가 별도 처리 (eventually consistent — v1 은 검증 미루고 v2 에서 archive consumer 추가).
- **Kafka 토픽 race**: e2e 시나리오들이 같은 토픽을 공유하면 다른 시나리오의 메시지를 잘못 잡을 수 있음 (TASK-MONO-023d 와 동일 함정). 각 시나리오의 fixture 를 unique stage_name + post_body 로 차별화.
- **시간 지연 (outbox relay 폴링)**: `outbox.polling.interval` 을 짧게 (e.g., 500ms) 설정한 e2e profile.

---

# Failure Scenarios

- **Docker daemon 미가동 (Windows native JVM)**: `@Testcontainers(disabledWithoutDocker = true)` 로 graceful skip. dev 머신에서는 WSL 또는 CI 에서 실행.
- **Boot jar 빌드 실패**: e2eTest 가 fail-fast (jar 가 없으면 시작 단계에서 명확한 에러).
- **JWKS endpoint 응답 실패**: gateway 의 startup probe (`JwksHealthProbe`) 가 fail-fast — 컨테이너 부팅 실패 → 테스트 인프라 자체 실패. 알람 가능.
- **CI runner OOM**: 3 service + postgres + redis + kafka + WireMock = 6+ 컨테이너. observability 컨테이너는 e2e 에서 제외 (ecommerce 와 동일 패턴).
- **Flaky timing**: Awaitility timeout 충분히 (15-30s) + retry 없음 (race 가 있으면 진짜 버그).

---

# Test Requirements

- e2eTest 만 (`@Tag("e2e")`)
- 3 시나리오 클래스 + base abstract
- contract 검증은 service 별 contract test 가 이미 담당 — e2e 는 흐름 검증에 집중
- 성능 검증은 별도 task

---

# Definition of Done

- [ ] `tests/e2e/` 모듈 + build.gradle + settings.gradle include
- [ ] 3 시나리오 + base 클래스 + JwtTestHelper 작성
- [ ] WireMock JWKS + Testcontainers 인프라 wiring
- [ ] 모든 시나리오 로컬 (Docker 환경) 통과
- [ ] CI `fan-platform-e2e` job 추가 + GREEN
- [ ] `specs/integration/v1-e2e-scenarios.md` 작성
- [ ] Ready for review
