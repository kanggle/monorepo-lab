# Task ID

TASK-FAN-BE-037

# Title

`JWT_JWKS_URI` 기본값이 IAM 실제 엔드포인트(`/oauth2/jwks`)와 불일치 — 5개 서비스 전부

# Status

in-progress

# Owner

fan-platform

# Task Tags

- config
- security
- test

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`iam-platform`의 `auth-service`가 실제로 서빙하는 JWKS 엔드포인트는 `/oauth2/jwks`이다(`OAuth2AuthorizationServerIntegrationTest.java`가 `GET /oauth2/jwks`를 직접 단언, Spring Authorization Server의 표준 라우트). ecommerce/scm/erp/finance 4개 프로젝트는 전부 이 경로를 기본값으로 올바르게 사용한다.

**fan-platform만 다르다.** 5개 서비스(gateway/community/artist/membership/notification) 전부:

- `docker-compose.yml`: `JWT_JWKS_URI: ${JWT_JWKS_URI:-http://iam.local/.well-known/jwks.json}`
- `application.yml`: `jwk-set-uri: ${OIDC_JWK_SET_URI:${JWT_JWKS_URI:${OIDC_ISSUER_URL:http://iam.local}/.well-known/jwks.json}}`
- `.env.example`: `JWT_JWKS_URI=http://iam.local/.well-known/jwks.json`

기본값이 전부 `/.well-known/jwks.json`으로 고정돼 있다 — IAM은 이 경로를 서빙하지 않으므로 env override 없이 실제 `iam.local`에 붙이면 404가 난다.

**이것은 스펙-구현 불일치이며, fan-platform 자신의 스펙 안에서도 갈라져 있다**:

| 문서/코드 | 값 |
|---|---|
| `specs/integration/iam-integration.md` (프로젝트 대표 통합 계약) | `${OIDC_ISSUER_URL}/oauth2/jwks` ✅ |
| `specs/services/membership-service/dependencies.md` | `${OIDC_ISSUER_URL}/oauth2/jwks` ✅ |
| `specs/services/community-service/dependencies.md`, `artist-service/dependencies.md` | `.well-known/jwks.json` ❌ |
| `specs/services/gateway-service/overview.md` | `.well-known/jwks.json` ❌ |
| 5개 서비스 `application.yml`/`docker-compose.yml`/`.env.example` 전부 | `.well-known/jwks.json` ❌ |
| 5+1개 테스트 더블 `JwksMockServer` (gateway/community/artist/membership/notification/e2e) | `/.well-known/jwks.json`만 서빙(membership만 `/oauth2/jwks`도 추가 서빙) |

즉 `membership-service`는 **자기 자신의 스펙**(`dependencies.md`)이 `/oauth2/jwks`라고 명시하는데, **자기 자신의 `application.yml` 기본값**은 `.well-known/jwks.json`이다 — 코드가 스펙을 어긴 것이지 아키텍처 결정 사안이 아니다.

이 결함이 지금까지 드러나지 않은 이유: 전 서비스의 유닛/통합 테스트가 `JwksMockServer`(호스트 JVM mock)로 `/.well-known/jwks.json`을 셀프-서빙하므로, 실제 IAM `iam.local`에 라이브로 붙는 경로(로컬 데모, 프로덕션형 배선)만 이 결함을 드러낸다 — 실제로 최근 로컬 데모 검증 중 발견됨(`projects/fan-platform/docker-compose.iamlocal.yml`, 미커밋 로컬 오버레이 주석에 이미 기록됨).

---

# Scope

## In Scope

**스펙 정정 (구현보다 먼저)** — 정답(`iam-integration.md`, `membership-service/dependencies.md`)에 맞춰 갈라진 쪽을 통일:
- `specs/services/community-service/dependencies.md` — JWKS 라인 `/oauth2/jwks`로 정정
- `specs/services/artist-service/dependencies.md` — 동일
- `specs/services/gateway-service/overview.md` — JWT validation 라인 `/oauth2/jwks`로 정정

**기본값 수정 (5개 서비스 전부, `.well-known/jwks.json` → `/oauth2/jwks`)**:
- `docker-compose.yml` (gateway/community/artist/membership/notification, 5곳)
- `.env.example`
- `apps/{gateway,community,artist,membership,notification}-service/src/main/resources/application.yml` (파생 폴백의 마지막 segment)

**테스트 더블 정합 (프로덕션 기본값과 일치시켜 드리프트 재발 방지)**:
- `apps/{gateway,community,artist,membership,notification}-service/.../testsupport/JwksMockServer.java` — 서빙 경로를 `/oauth2/jwks`로 변경(membership은 dual-serve였던 것을 단일 경로로 정리하거나 유지 — 구현 시 판단)
- `tests/e2e/.../testsupport/JwksMockServer.java` — 동일
- 위 mock server 경로에 의존하는 테스트 단언(`TenantClaimValidatorTest.java:21`, `OAuth2ResourceServerConfigTest.java:121` 등 리터럴 `.well-known/jwks.json` 문자열)을 `/oauth2/jwks`로 갱신
- `specs/integration/v1-e2e-scenarios.md` — mock 서버 서빙 경로 서술 갱신

## Out of Scope

- `OIDC_JWK_SET_URI`/`JWT_JWKS_URI` env var 자체의 이름/우선순위 체계 변경 (3단 폴백 구조는 유지, 마지막 폴백값만 수정).
- iam-platform 측 실제 라우트 변경 (IAM은 올바르다 — 소비측만 정정).
- `projects/fan-platform/docker-compose.iamlocal.yml` 등 미커밋 로컬 데모 오버레이 파일 삭제/수정(별도 판단 — 이 task로 기본값이 고쳐지면 해당 오버레이의 `JWT_JWKS_URI` 라인은 불필요해지지만 파일 자체 정리는 범위 밖).
- `TASK-MONO-490`(iam.local 자체가 Traefik 라우팅 안 되는 문제) — 별개 축, root task.

---

# Acceptance Criteria

- [ ] `grep -r "well-known/jwks" projects/fan-platform/` → **0건**(과거 `tasks/done/` 기록 제외 — HARDSTOP-05상 편집 대상 아님).
- [ ] 5개 서비스 `application.yml`/`docker-compose.yml`/`.env.example`의 JWKS 기본값이 전부 `/oauth2/jwks`로 일치.
- [ ] `community-service`/`artist-service`/`gateway-service` 스펙이 `iam-integration.md`/`membership-service` 스펙과 표현이 일치.
- [ ] 5개 서비스 `:check` + `tests/e2e` 전부 GREEN, 테스트 개수 무회귀(mock 서버 경로만 바뀌고 테스트 로직/커버리지는 불변임을 증명).
- [ ] `docker-compose.iamlocal.yml`(미커밋 로컬 오버레이) 없이도 `iam-platform`의 실제 `/oauth2/jwks`에 대해 `curl`로 JWKS 조회가 성공함을 로컬에서 1회 실증(README/PR 본문에 기록).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`(domain=`fan-platform`, traits=`[transactional, content-heavy, read-heavy, integration-heavy, multi-tenant]`) → `rules/common.md` + 해당 trait 파일들.

- `projects/fan-platform/specs/integration/iam-integration.md` (§ OIDC Endpoints — 정답 소스)
- `projects/fan-platform/specs/services/{gateway,community,artist,membership,notification}-service/dependencies.md`
- `projects/fan-platform/specs/services/gateway-service/overview.md`
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md`
- `projects/iam-platform/specs/features/consumer-integration-guide.md` (IAM측 정경 — 대조용)

# Related Skills

- (백엔드 설정/테스트 수정 — 특수 skill 불필요)

---

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (JWKS 엔드포인트 계약 — 있다면 대조)

---

# Edge Cases

- `membership-service`의 `JwksMockServer`는 이미 두 경로(`/.well-known/jwks.json`, `/oauth2/jwks`)를 모두 서빙한다 — 나머지 4개와 다른 이 비대칭을 통일할지, 유지할지 구현 시 명시적으로 결정하고 이유를 남긴다.
- env override로 `JWT_JWKS_URI`를 직접 지정하는 기존 배포/CI 설정이 있다면(예: `docker-compose.e2e.yml`의 `JwtTestHelper` 경유 주입) 기본값 변경의 영향을 받지 않음을 확인한다 — 폴백값만 바뀌므로 명시적 override는 무영향이어야 한다.

---

# Failure Scenarios

- 기본값만 고치고 테스트 더블을 안 고치면 테스트는 계속 초록이지만 실제 배선은 여전히 검증되지 않는 상태로 남는다 — 반드시 mock server 경로도 함께 정정한다.
- 스펙 정정 없이 코드만 고치면 스펙 간 불일치가 남아 다음 사람이 다시 갈라진 스펙 중 하나를 "정답"으로 오인할 수 있다 — 스펙 정정을 코드 수정과 함께(먼저) 포함한다.
