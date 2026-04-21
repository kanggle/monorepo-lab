# Task ID

TASK-BE-031

# Title

gateway-service 부트스트랩 — Spring Cloud Gateway, JWT 검증 필터, 라우팅, Rate Limiting, 요청 로깅

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Goal

gateway-service를 실행 가능한 Spring Cloud Gateway 애플리케이션으로 구현한다.
모든 외부 요청의 단일 진입점으로서 JWT 검증, 헤더 주입, 속도 제한, 요청 로깅, CORS를 처리한다.

이 태스크 완료 후: 외부 요청이 gateway를 통해 하위 서비스로 라우팅되며, 보호된 경로는 유효한 JWT 없이 접근 불가하다.

---

# Scope

## In Scope

- build.gradle 의존성 설정 (Spring Cloud Gateway, Redis Reactive, JWT, Actuator)
- GatewayApplication 엔트리포인트
- JwtAuthenticationFilter (GlobalFilter): 공개 경로 제외, JWT 검증, X-User-Id/X-User-Email 헤더 주입
- RequestLoggingFilter (GlobalFilter): 메서드, 경로, 상태 코드, 응답 시간 로깅
- RateLimiterConfig: IP 기반 KeyResolver, Redis 기반 RequestRateLimiter
- application.yml: 라우트 정의 (auth/product/search/order/payment), 공개 경로, Redis, JWT 설정
- CORS 설정 (globalcors)
- 표준 에러 응답 포맷 (code, message, timestamp) 반환 — 401
- /actuator/health 엔드포인트
- 단위 테스트: JwtAuthenticationFilter (Spring 컨텍스트 없음)
- 통합 테스트: 미인증 요청 → 401, 공개 경로 → 필터 통과

## Out of Scope

- 서비스 디스커버리 (Eureka/Consul)
- mTLS, HTTPS 종료
- 서킷 브레이커

---

# Acceptance Criteria

- [ ] `./gradlew :apps:gateway-service:bootRun` 으로 애플리케이션이 기동된다
- [ ] `/actuator/health` 가 `{"status":"UP"}` 을 반환한다
- [ ] 보호된 경로에 토큰 없이 요청 시 `401 UNAUTHORIZED` + 표준 에러 JSON을 반환한다
- [ ] 보호된 경로에 유효한 JWT로 요청 시 `X-User-Id`, `X-User-Email` 헤더가 주입되어 하위 서비스로 전달된다
- [ ] 공개 경로(`POST /api/auth/login` 등)는 JWT 없이 통과된다
- [ ] 단위 테스트 통과: JwtAuthenticationFilter 경로별 동작 검증
- [ ] 통합 테스트 통과: 미인증 → 401, 공개 경로 → 필터 통과

---

# Related Specs

- `specs/platform/api-gateway-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/implementation-workflow.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

없음 (gateway는 계약을 소유하지 않음)

---

# Target Service

- `gateway-service`

---

# Architecture

Spring Cloud Gateway (WebFlux 기반 리액티브). Layered 아키텍처 불필요 — 필터/설정 구조:

```
com.example.gateway
├── GatewayApplication.java
├── filter/
│   ├── JwtAuthenticationFilter.java   (GlobalFilter + Ordered)
│   └── RequestLoggingFilter.java      (GlobalFilter + Ordered)
└── config/
    └── RateLimiterConfig.java         (KeyResolver, RateLimiter 빈)
```

---

# Implementation Notes

- Spring Cloud Gateway 2024.0.x (Spring Boot 3.4.x 호환)
- JWT 라이브러리: `io.jsonwebtoken` 0.12.6 (auth-service와 동일)
- Rate Limiting: Redis 기반 `RequestRateLimiter` 필터 — IP 기반 KeyResolver
  - 기본: 100 req/min, auth 경로: 10 req/min
- 공개 경로 (스펙 기준):
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `GET /api/products/**`
  - `GET /api/search/**`
  - `GET /actuator/health`
- 401 에러 응답: `{"code":"UNAUTHORIZED","message":"...","timestamp":"..."}`
- JWT claims에서 `sub` → X-User-Id, `email` → X-User-Email 추출

---

# Edge Cases

- Authorization 헤더가 `Bearer ` 접두사 없이 오는 경우 → 401
- JWT 만료 시 → 401
- 공개 경로에 POST/GET 메서드가 맞지 않으면 보호된 경로로 처리
- Redis 미연결 시 Rate Limiter 동작 불가 — 기동 실패 허용

---

# Failure Scenarios

- JWT secret 미설정 → 기동 실패 (정상)
- Redis 미연결 → 기동 실패 (정상)
- 하위 서비스 미연결 → 502/503 반환 (정상)

---

# Test Requirements

- 단위 테스트 (`JwtAuthenticationFilterTest`):
  - 인증 헤더 없음 → 401
  - 잘못된 JWT → 401
  - 유효한 JWT + 보호된 경로 → 필터 통과 (chain.filter 호출)
  - 공개 경로 → 토큰 없이 필터 통과
- 통합 테스트 (`GatewayIntegrationTest`):
  - Testcontainers Redis 사용
  - 미인증 요청 → 401
  - 유효 JWT 요청 → 401 아님 (하위 서비스 불가 → 5xx 허용)
  - 공개 경로 → 401 아님

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
