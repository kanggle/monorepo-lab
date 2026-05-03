# Task ID

TASK-FAN-BE-005

# Title

fan-platform gateway-service `RewritePath` 누락 production bug fix

# Status

ready

# Owner

backend

# Task Tags

- code

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

fan-platform gateway-service 의 `application.yml` 에 `RewritePath` 필터가 누락되어 있어, 외부 client 가 `/api/v1/community/**` / `/api/v1/artist/**` 로 접근 시 다운스트림 서비스에서 404 반환. TASK-FAN-INT-001 e2e 구현 시 발견.

다운스트림 서비스 routes:
- community-service: `/api/community/posts`, `/api/community/feed`, `/api/community/posts/{id}/comments`, `/api/community/posts/{id}/reactions`, `/api/community/follows`
- artist-service: `/api/artists`, `/api/artist-groups`, `/api/fandoms`

gateway 의 외부 API 는 v1 namespace 를 가져야 하므로 (`/api/v1/community/**`, `/api/v1/artist/**`):

```yaml
spring.cloud.gateway.routes:
  - id: community
    uri: ${COMMUNITY_SERVICE_URI:http://community-service:8080}
    predicates:
      - Path=/api/v1/community/**
    filters:
      - RewritePath=/api/v1/community/(?<segment>.*), /api/community/${segment}
  - id: artist
    uri: ${ARTIST_SERVICE_URI:http://artist-service:8080}
    predicates:
      - Path=/api/v1/artist/**
    filters:
      - RewritePath=/api/v1/artist/(?<segment>.*), /api/${segment}    # /api/v1/artist/groups → /api/artist-groups (or similar)
```

이 태스크 완료 후:
- `pnpm fan-platform:up` + `curl http://fan-platform.local/api/v1/community/posts` (with auth) 가 community-service 로 routing
- `curl http://fan-platform.local/api/v1/artist/artists` 가 artist-service 의 `/api/artists` 로 routing
- TASK-FAN-INT-001 e2e suite 의 `SPRING_APPLICATION_JSON` 우회가 production code 에 흡수됨

---

# Scope

## In Scope

### 1. Gateway routes 정정
- `projects/fan-platform/apps/gateway-service/src/main/resources/application.yml` 의 `spring.cloud.gateway.routes` 갱신
  - community route: `Path=/api/v1/community/**` + `RewritePath=/api/v1/community/(?<segment>.*), /api/community/${segment}`
  - artist route: artist-service 의 multiple base paths (`/api/artists`, `/api/artist-groups`, `/api/fandoms`) 처리. 두 가지 옵션:
    - **A**: gateway 측 단일 prefix `/api/v1/artist/**` → 다운스트림 path mapping (e.g., `/api/v1/artist/groups → /api/artist-groups`). RewritePath regex 복잡도 증가.
    - **B**: gateway 측 3 routes (artist / artist-group / fandom) — 더 명시적, 다운스트림 path 와 1:1 매핑.
  - 권장 **B** (명시성 > 단일 entry).

### 2. e2e 테스트 정리
- `projects/fan-platform/tests/e2e/.../FanPlatformE2ETestBase.java` 의 SPRING_APPLICATION_JSON 우회 제거 (TASK-FAN-INT-001 deviation 흡수)
- e2e 가 production gateway routes 그대로 사용해도 통과해야 함

### 3. spec 갱신
- `projects/fan-platform/specs/contracts/http/community-api.md`, `artist-api.md` 의 endpoint 표가 외부 noun (`/api/v1/community/...`) 을 명시하는지 확인. 내부 path 와 외부 path 구분이 명확해야 함.
- `projects/fan-platform/specs/services/gateway-service/architecture.md` 의 Routes 섹션에 RewritePath 정책 명시.

### 4. 단위/슬라이스 테스트
- gateway-service 의 routing 단위 테스트 추가 — `/api/v1/community/posts` request 가 `community-service:8080/api/community/posts` 로 forward 되는지 검증 (WebTestClient + WireMock 다운스트림).

## Out of Scope

- 다른 path 규약 (예: `/api/v2/*` 도입) — v1 스펙 유지
- gateway 의 OAuth2 / rate-limit / tenant 검증 로직 변경 — TASK-FAN-BE-001 / 004 에서 이미 처리
- e2e 테스트 추가 (e2e suite 자체 변경은 우회 제거에 한함)
- 다른 프로젝트 (ecommerce / wms / GAP) 의 gateway

---

# Acceptance Criteria

- [ ] `./gradlew :projects:fan-platform:apps:gateway-service:check --no-daemon` 통과
- [ ] gateway 단위 테스트가 RewritePath 검증 (community + artist + artist-group + fandom 4 cases)
- [ ] TASK-FAN-INT-001 e2e suite (PR #131 머지 후) 의 SPRING_APPLICATION_JSON 우회 제거 — 같은 PR 또는 직후 cleanup PR
- [ ] 로컬에서 `pnpm fan-platform:up` + curl 검증 (`http://fan-platform.local/api/v1/community/posts` Authorization 헤더 포함 → community-service 응답)
- [ ] specs/services/gateway-service/architecture.md 의 Routes 섹션에 RewritePath 정책 명시

---

# Related Specs

- `projects/fan-platform/specs/services/gateway-service/architecture.md`
- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`
- `projects/fan-platform/tasks/done/TASK-FAN-INT-001-v1-services-e2e.md` (when merged) — 발견 경위
- Spring Cloud Gateway 공식 문서 (RewritePath filter)

# Related Skills

- `.claude/skills/backend/gateway-security/SKILL.md`
- `.claude/skills/testing/test-strategy/SKILL.md`

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/contracts/http/artist-api.md`

---

# Target Service / Component

- `projects/fan-platform/apps/gateway-service/src/main/resources/application.yml` (라우트 갱신)
- `projects/fan-platform/apps/gateway-service/src/test/java/.../GatewayRouteRewriteTest.java` (신규)
- `projects/fan-platform/specs/services/gateway-service/architecture.md` (Routes 섹션 갱신)
- `projects/fan-platform/tests/e2e/src/test/java/.../testsupport/FanPlatformE2ETestBase.java` (우회 제거)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. gateway-service 의 라우팅 패턴은 그대로, route 정의만 정정.

---

# Implementation Notes

- **PR #131 (TASK-FAN-INT-001) 머지 후** 진행 — e2e SPRING_APPLICATION_JSON 우회 코드를 같은 PR 에서 정리할 수 있음.
- `RewritePath` regex 는 Spring Cloud Gateway 의 named-group capture 사용. Spring 5.x / 6.x 호환.
- artist-service 는 다수 base paths (`/api/artists`, `/api/artist-groups`, `/api/fandoms`) — gateway 측에서 3 routes 로 분리하거나 단일 route + 복합 RewritePath. **명시적 분리 권장**.
- gateway 단위 테스트는 `WebTestClient` 와 `WireMock` 로 다운스트림 mock + 라우팅 검증.

---

# Edge Cases

- **`/api/v1/community/posts/{id}/reactions` (path variable)**: RewritePath regex 가 segment 전체 capture — `${segment}` 가 `posts/{id}/reactions` 그대로 보존되어야 함.
- **빈 query string**: `?` 가 있는 경우 RewritePath 가 query string 영향 없도록 (Spring Cloud Gateway 기본 동작 그대로).
- **`/api/v1/artist/...` 와 다운스트림 base path 매핑**: artist-service 의 `/api/artist-groups` (artist 와 별도 noun) — gateway 측 외부 path 결정 필요. 권장: `/api/v1/artist-groups/...` (외부도 분리) — 단순함.

---

# Failure Scenarios

- **RewritePath regex 오류**: gateway 가 startup 실패 (Spring Cloud Gateway 가 invalid filter spec 거부). 로컬에서 즉시 catch.
- **다운스트림 path 변경**: community-service 의 `/api/community/...` 가 v2 에서 변경되면 gateway 도 동기화 필요. 컨트랙트 spec 으로 매핑 보장.
- **e2e suite 가 우회 제거 후 fail**: 진짜 RewritePath 가 다운스트림 path 와 다르다는 신호. fix 의 일부.

---

# Test Requirements

- 단위:
  - `GatewayRouteRewriteTest` — 4 cases (community / artist / artist-group / fandom)
- 통합:
  - 기존 `GatewayBootstrapIntegrationTest` 가 RewritePath 시나리오 1건 추가 검증
- e2e:
  - TASK-FAN-INT-001 의 우회 제거 후 GREEN

---

# Definition of Done

- [ ] gateway `application.yml` 라우트 정정 (community + artist + artist-group + fandom)
- [ ] gateway 단위 + 통합 테스트 추가 / 갱신
- [ ] TASK-FAN-INT-001 e2e 우회 제거 (같은 PR 또는 직후)
- [ ] 로컬 smoke 통과 (`pnpm fan-platform:up` + curl)
- [ ] specs/services/gateway-service/architecture.md 갱신
- [ ] Ready for review
