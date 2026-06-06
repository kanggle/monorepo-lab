# Task ID

TASK-BE-064

# Title

게이트웨이 admin 라우트 인증 경계 정합화 — `/api/admin/**` public-paths 누락 해소

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- spec

# depends_on

(없음)

---

# Goal

게이트웨이(`gateway-service`, :8080)가 **admin 계열 모든 요청을 `TOKEN_INVALID` 로 거부**한다. 원인은 `gateway.public-paths` 에 admin 경로가 전무하고, 동시에 gateway 의 `JwtAuthenticationFilter` 가 사용하는 JWKS 는 auth-service(account 토큰) 용이라 admin-service 가 발행한 operator 토큰을 검증할 수단이 없기 때문이다. 결과적으로 로그인(`POST /api/admin/auth/login`) 뿐 아니라 `lock`/`unlock`/`revoke`/`audit` 등 **인증이 필요한 admin 뮤테이션 엔드포인트도 게이트웨이 경유 시 전부 동작하지 않는다**.

[specs/contracts/http/admin-api.md](../../specs/contracts/http/admin-api.md) 는 인증 예외 5개 경로를 명시하고, 뮤테이션 경로들은 admin-service 의 `OperatorAuthenticationFilter` 가 "별도 인증 필터"로 operator 토큰을 검증하는 설계를 전제로 한다. [specs/contracts/http/gateway-api.md](../../specs/contracts/http/gateway-api.md) 의 Route Map 은 이를 반영하지 못해 양 스펙이 서로 모순된 상태다.

발견 경로: 로컬 admin-web → gateway:8080 → admin login 호출이 TOKEN_INVALID 로 501 실패(2026-04-19).

---

# Scope

## 현 상태 분석

**스펙 간 불일치**
- `admin-api.md` §"Authentication Exceptions" (L22–L29): 아래 5경로는 operator JWT 없이 접근 가능해야 함
  - `POST /api/admin/auth/login`
  - `POST /api/admin/auth/2fa/enroll` (bootstrap token)
  - `POST /api/admin/auth/2fa/verify` (bootstrap token)
  - `POST /api/admin/auth/refresh` (refresh JWT in body)
  - `GET  /.well-known/admin/jwks.json`
- `gateway-api.md` §"Route Map" (L9–L22): 위 5경로 라인 자체가 없고, 인증 필요 admin 뮤테이션 4건에만 "별도 인증 필터" 주석이 달려 있음. "별도 인증 필터" 가 gateway 인증 스킵을 의미하는지 명시적 문장 부재.

**구현 상태**
- [gateway-service/application.yml:75–82](../../apps/gateway-service/src/main/resources/application.yml): `public-paths` 에 admin 경로 0건
- [JwtAuthenticationFilter.java:61](../../apps/gateway-service/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java): public 이 아니면 JWT 검증 → 실패 시 `TOKEN_INVALID`
- gateway JWT 검증기는 `auth-service JWKS` 만 로딩 ([application.yml:55](../../apps/gateway-service/src/main/resources/application.yml)) — operator 토큰(admin-service 서명) 검증 불가
- admin-service 의 [OperatorAuthenticationFilter.shouldNotFilter](../../apps/admin-service/src/main/java/com/example/admin/infrastructure/security/OperatorAuthenticationFilter.java#L57-L79) 는 위 5경로를 이미 올바르게 bypass — 게이트웨이 쪽만 미러링되면 됨

## In Scope

**A. 스펙 정합화 — `gateway-api.md`**
1. Route Map 에 admin 인증 예외 5경로 라인 추가 (인증 필요 = No, 비고 = "admin-service self-serve auth")
2. admin 뮤테이션 4경로의 "별도 인증 필터" 주석을 명시적 문장으로 확장: "gateway 는 JWT 검증을 스킵하고 admin-service `OperatorAuthenticationFilter` 가 operator JWT 를 검증함"
3. 가능하면 별도 §"Admin Routes (second-layer auth)" 섹션 신설로 설계 의도 기술

**B. 구현 — `gateway-service`**
4. `application.yml` `gateway.public-paths` 에 아래 9건 추가 (gateway 관점에서 모두 public — operator 검증은 downstream 전담):
   - `POST:/api/admin/auth/login`
   - `POST:/api/admin/auth/2fa/enroll`
   - `POST:/api/admin/auth/2fa/verify`
   - `POST:/api/admin/auth/refresh`
   - `GET:/.well-known/admin/jwks.json`
   - `POST:/api/admin/accounts/*/lock`
   - `POST:/api/admin/accounts/*/unlock`
   - `POST:/api/admin/sessions/*/revoke`
   - `GET:/api/admin/audit`
5. (선택) `public-paths` 가 장문으로 늘어나면 `admin-public-paths` 로 분리하거나, 코드 레벨에서 `/api/admin/**` 를 전체 bypass 로 취급하는 전용 pre-check 도입 검토. AC 에는 포함 안 함 — 현 구조 유지 전제.

**C. Rate-limit 스코프 확인**
6. [RouteConfig.resolveRateLimitScope](../../apps/gateway-service/src/main/java/com/example/gateway/route/RouteConfig.java#L52-L63) 가 admin login 에 `login` 스코프를 적용하지 않음 (prefix `/api/auth/login` 만 매칭). admin login 에 별도 스코프가 필요한지 결정 → 필요 시 `admin-login` 스코프 추가(본 태스크에선 결정만 내리고 구현은 후속 태스크로 분리해도 됨).

## Out of Scope

- operator JWT 를 게이트웨이에서 검증하도록 JWKS multi-source 지원 (ADR 수준 변경, 별도 과제)
- admin 신규 엔드포인트 추가
- rate-limit `admin-*` 스코프 실제 구현 (본 태스크는 결정까지)
- CSP 등 admin-web 쪽 정책 조정

---

# Acceptance Criteria

- [ ] gateway 기동 후 `curl -X POST :8080/api/admin/auth/login -H 'Content-Type: application/json' -d '{"operatorId":"admin","password":"devpassword123!"}'` 가 `TOKEN_INVALID` 아닌 admin-service 응답(예: `ENROLLMENT_REQUIRED` 401 또는 200 token pair) 반환
- [ ] 유효 operator 토큰으로 `POST :8080/api/admin/sessions/{id}/revoke` 호출 시 gateway 가 JWT 검증을 스킵하고 admin-service 까지 프록시. admin-service 의 `OperatorAuthenticationFilter` 가 단일 검증 지점으로 동작
- [ ] `GET :8080/.well-known/admin/jwks.json` → 200, JWKS JSON 반환
- [ ] admin 경로 외 기존 public/private 경로 동작 회귀 없음 (auth login/signup/refresh, account me 등)
- [ ] `gateway-api.md` 가 `admin-api.md` §Authentication Exceptions 와 경로·메서드·인증요건 면에서 완전 일치
- [ ] gateway-service 테스트 추가: `RouteConfigTest` 에 admin 5+4 경로가 `isPublicRoute()` true 를 반환하는 케이스. `JwtAuthenticationFilterTest` 또는 통합 테스트로 admin 경로가 JWT 없이 downstream 으로 라우팅되는지 확인
- [ ] `./gradlew :apps:gateway-service:test :apps:gateway-service:check` green
- [ ] 전체 `./gradlew build` green

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `specs/services/admin-service/security.md` (operator JWT / bootstrap token 정의)
- `specs/services/admin-service/architecture.md`
- `platform/architecture-decision-rule.md` (second-layer auth 를 명시적 설계로 기록할지 여부)

---

# Related Contracts

- 수정: `specs/contracts/http/gateway-api.md` (Route Map + Admin Routes 섹션)
- 참조: `specs/contracts/http/admin-api.md` §Authentication Exceptions, §"Error Codes"

---

# Target Service

- `apps/gateway-service` (primary — public-paths, 필요 시 RouteConfig)
- 스펙: `specs/contracts/http/gateway-api.md`

---

# Architecture

gateway-service 는 4-layer 가 아닌 reactive filter-chain 구조. `JwtAuthenticationFilter` (GlobalFilter, order −100) 가 auth 경계를 담당하고, admin 뮤테이션은 "game-keeper 스타일 2차 검증" 을 downstream(admin-service) 에 위임하는 기존 패턴을 유지한다. 이 태스크는 해당 위임이 의도대로 통하도록 public-paths 리스트를 현실과 일치시킨다.

단일 검증 지점 원칙에 따라 **한 요청을 두 곳(gateway, admin-service)에서 동시에 JWT 검증하는 일은 없다.** admin 경로는 gateway 입장에서 public, admin-service 입장에서 protected 로 취급된다.

---

# Edge Cases

- `AntPathMatcher` 가 `/api/admin/accounts/*/lock` 같은 단일 세그먼트 wildcard 를 지원하는지 확인 (현 matcher 기본 동작). 실패 시 `/api/admin/accounts/**/lock` 패턴으로 대체하거나 prefix 기반으로 완화
- rate-limit 미적용 상태에서 admin login 폭주 가능성 → Scope(C) 에서 결정
- admin 토큰이 이미 노출된 상태에서 `POST /api/admin/auth/refresh` 가 public 으로 열려 있지만, admin-service 내부에서 refresh JWT 재사용(replay) 검증은 [TASK-BE-040](../done/TASK-BE-040-admin-refresh-logout.md) 로 처리됨 — 본 태스크에서 추가 검증 없음
- `/.well-known/admin/jwks.json` 은 GET 이므로 method matching 을 반드시 `GET:` 으로 고정 (POST 까지 열리지 않도록)
- 과거에 CDN/상위 프록시가 `/api/admin/auth/*` 를 별도 정책으로 막고 있었는지 확인 (infra 구성 영향도)

---

# Failure Scenarios

- `public-paths` 순서/철자 오타 → 해당 경로만 TOKEN_INVALID 유지. 테스트로 회귀 감지
- admin-service 의 `OperatorAuthenticationFilter` 가 향후 deprecated 되면 gateway public 으로 남은 admin 뮤테이션은 **완전 무인증** 노출. 본 태스크에서는 두 층의 연동 불변식을 `architecture.md` 또는 주석으로 명시 기록할 것
- operator 토큰 복호화 책임 위치를 추후 gateway 로 이전하는 결정이 내려지면 본 태스크의 public-paths 대부분은 다시 좁아져야 함 (별도 ADR 대상)

---

# Test Requirements

- gateway 단위 테스트: `RouteConfigTest` 에 admin 경로 9건 + 기존 public/private 경로의 `isPublicRoute()` 매트릭스
- gateway slice/필터 테스트: `JwtAuthenticationFilter` 가 admin 경로에 대해 JWT 없이 chain 통과시키는지
- gateway 통합 테스트(WireMock 또는 admin-service stub): `POST :gateway/api/admin/auth/login` → admin-service 가 받은 요청에 `Authorization` 이 없거나 원본 그대로 전달됨을 확인
- 스펙 정합 검증: `admin-api.md` 와 `gateway-api.md` 의 인증 예외 목록이 diff 없이 일치 (CI 에 text-level 체커가 있다면 활용)

---

# Definition of Done

- [ ] `gateway-api.md` / `admin-api.md` 정합 업데이트
- [ ] `application.yml` public-paths 확장
- [ ] 단위/슬라이스/통합 테스트 추가
- [ ] 로컬 smoke: admin-web (`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`) 에서 로그인 폼 제출 시 502/401 TOKEN_INVALID 가 사라지고 실제 admin-service 응답(ENROLLMENT_REQUIRED 또는 로그인 성공) 수신
- [ ] `./gradlew build` CI green
- [ ] Ready for review
