# Task ID

TASK-BE-508

# Title

사용자 세션 관리 4개 엔드포인트가 게이트웨이 경유로 도달 불가 (auth-service 라우트 부재 → 500)

# Status

done

# Owner

backend

# Task Tags

- code
- api

---

# 🔴 발견 경위 (2026-07-15 라이브 풀스택 스윕)

`docker-compose.e2e.yml` 로 IAM 5개 서비스를 실제 기동해 게이트웨이 경유로 HTTP 를 호출하던 중 발견. **nightly `E2E full (iam docker-compose)` 는 초록** 이었다 — 그 6개 e2e 클래스가 **운영자 플로우**만 검증하고 **게이트웨이를 통한 사용자 세션 경로**를 건드리지 않기 때문. 아래 수치·경로는 **그 시점 실측 스냅샷**이다. **착수 시 재현부터 재측정할 것(코드가 이긴다).**

---

# Goal

사용자 세션 관리 엔드포인트(`GET /api/accounts/me/sessions`, `GET /api/accounts/me/sessions/current`, `DELETE /api/accounts/me/sessions/{deviceId}`, `DELETE /api/accounts/me/sessions`)를 **게이트웨이 경유로 정상 동작**하게 만든다.

이 4개는 **auth-service** 의 `AccountSessionController` 가 구현하는데, 게이트웨이 라우트 맵에는 `/api/accounts/**` → **account-service** 하나뿐이라 세션 호출이 **account-service 로 오배송**된다. account-service 에 해당 핸들러가 없으니 Spring 이 `NoResourceFoundException` 을 던지고, 그것이 공유 예외 핸들러에서 500 으로 변질된다(→ 별개 결함 `TASK-MONO-415`).

## 실측 근거 (2026-07-15, 재측정 대상)

- auth-service **직접** 호출 → `400 Missing required header: X-Account-Id` (핸들러 살아있음. 게이트웨이가 주입해야 할 헤더가 없을 뿐)
- **게이트웨이 경유** → `500 INTERNAL_ERROR`; account-service 로그에 `NoResourceFoundException: No static resource api/accounts/me/sessions`
- OIDC(authorization_code+PKCE) 사용자 토큰으로도 결과 동일 — **인증 문제가 아니라 라우팅 문제**

# Root Cause

`apps/gateway-service/src/main/resources/application.yml` 의 `spring.cloud.gateway.routes` 에 `/api/accounts/me/sessions/**` 를 **auth-service** 로 보내는 라우트가 없다. `/api/accounts/**` → account-service 라우트가 세션 경로까지 삼킨다.

# Scope

## In Scope
- `apps/gateway-service/src/main/resources/application.yml`: `/api/accounts/me/sessions`, `/api/accounts/me/sessions/**` 를 **auth-service** 로 보내는 라우트 추가. Spring Cloud Gateway 는 **선언 순서로 매칭**하므로 이 세션 라우트를 기존 `account-service`(`/api/accounts/**`) 라우트 **앞**에 둘 것.
- 게이트웨이 JWT 필터가 세션 경로에도 `X-Account-ID`/`X-Device-Id` 를 주입하는지 확인(기존 `/api/accounts/**` 와 동일 필터 체인이면 자동, 아니면 배선 추가).
- 계약 정합: `specs/contracts/http/gateway-api.md` 의 Route Map 에 세션 4경로 행 추가(현재 **누락**). `auth-api.md` 는 이미 4개를 선언 중 — gateway-api.md 만 뒤처져 있다.

## Out of Scope
- 세션 컨트롤러 로직 자체(정상 동작 — 헤더만 주어지면 200).
- 500 변질(공유 예외 핸들러)은 `TASK-MONO-415` 소관. 라우트가 고쳐지면 이 경로의 500 은 사라지지만, "없는 경로 = 500" 근본 결함은 그 티켓에서 별도 처리.

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 게이트웨이 경유 `GET /api/accounts/me/sessions` 가 **현재 500** 임을 유효 사용자 토큰으로 먼저 재현. (스냅샷과 다르면 코드 현황 우선 — 재조사.)
- [ ] 라우트 추가 후 게이트웨이 경유 세션 4경로가 **200/2xx** 반환(유효 토큰). 직접 호출 시 400 을 유발하던 `X-Account-Id` 를 게이트웨이가 주입함을 확인.
- [ ] 게이트웨이 경유 `GET /api/accounts/me`(account-service) 가 **여전히 정상** — 새 라우트가 프로필 경로를 훔치지 않음(순서·prefix 회귀 없음).
- [ ] `gateway-api.md` Route Map 에 세션 4경로 명시.
- [ ] `:check` GREEN + fed-e2e(IAM) IT 에 **게이트웨이 경유 세션 목록 조회** 시나리오 추가(회귀 방지 — 이 결함이 초록으로 새어나간 근본 원인이 커버리지 공백이므로 테스트를 반드시 추가).

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` + `rules/domains/saas.md` + 선언된 trait 파일들.

- `specs/features/session-management.md`
- `specs/services/gateway-service/architecture.md`
- `specs/services/auth-service/architecture.md`

# Related Contracts

- `specs/contracts/http/gateway-api.md` (Route Map — 세션 라우트 누락, 추가 대상)
- `specs/contracts/http/auth-api.md` (세션 4경로 선언 — 이미 존재)

# Target Service

- `gateway-service` (주), `auth-service` (라우팅 대상)

# Edge Cases

- `/api/accounts/me/sessions` 와 `/api/accounts/me`(profile) 의 prefix 충돌 — 세션은 auth-service, 프로필은 account-service. 라우트 **순서/구체성**으로 분리(세션 라우트가 더 구체적이므로 먼저).
- `DELETE /api/accounts/me/sessions/{deviceId}` 의 path variable 이 라우트 predicate 와 어긋나지 않는지.
- 게이트웨이 public-paths 목록에 세션 경로가 없어야 함(인증 필수 경로 — JWT 필터가 헤더 주입해야 하므로).

# Failure Scenarios

- 세션 라우트를 account-service 라우트 **뒤**에 두면 여전히 오배송(순서 함정).
- 라우트 predicate 를 너무 넓게(`/api/accounts/me/**`) 잡으면 프로필/상태 경로까지 auth-service 로 끌려가 account-service 기능이 죽는다.
- 게이트웨이가 `X-Account-Id` 를 주입 안 하면 200 대신 400 — 필터 체인 적용 여부를 라이브로 확인.

# Definition of Done

- [ ] AC-0 재측정으로 결함 재현
- [ ] 라우트 추가 + gateway-api.md 갱신
- [ ] 게이트웨이 경유 세션 4경로 2xx, 프로필/상태 회귀 0
- [ ] 게이트웨이 경유 세션 IT 추가, `:check` + fed-e2e GREEN
- [ ] Ready for review
