# Service Architecture — gateway-service

## Service

`gateway-service`

## Service Type

`rest-api` — 엣지 API 게이트웨이. 퍼블릭 HTTP 트래픽의 단일 진입점.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Thin Layered** (edge filter pipeline)

일반적인 4-layer(presentation/application/domain/infrastructure) 구조는 이 서비스에 과함. 게이트웨이는 비즈니스 로직·도메인 모델·영속성이 **없는** 서비스이고, 본질적으로 **요청을 변형·검증·라우팅하는 필터 체인**이다.

## Why This Architecture

- **도메인 로직 없음**: 게이트웨이는 다운스트림 서비스의 결정을 실행하지 않는다. JWT 서명 검증, rate limit, CORS, 요청 ID 주입 같은 *횡단 관심사*만 수행.
- **상태 없음(mostly stateless)**: 유일한 상태는 Redis 기반 rate limit 토큰 버킷과 JWKS 캐시 — 모두 만료·복원 가능한 휘발성 상태.
- **지연에 민감**: 모든 요청이 거쳐가므로 깊은 계층 호출 체인을 쓰면 p99가 악화됨. 필터 파이프라인이 가장 짧은 경로.
- **변경 사유의 단일성**: 게이트웨이가 바뀌는 이유는 거의 항상 *정책*(CORS 허용 목록, 인증 요구, 새 업스트림 경로) 변경이지 도메인 변경이 아님.

## Internal Structure Rule

```
apps/gateway-service/src/main/java/com/example/gateway/
├── GatewayApplication.java
├── filter/                   ← 필터 체인 (인증, rate limit, 요청 ID, 로깅)
│   ├── JwtAuthenticationFilter.java
│   ├── RateLimitFilter.java
│   ├── RequestIdFilter.java
│   └── LoggingFilter.java
├── route/                    ← 업스트림 라우트 설정
│   ├── RouteConfig.java
│   └── UpstreamHealthIndicator.java
├── security/                 ← JWKS 페치·캐시, 토큰 검증
│   ├── JwksClient.java
│   ├── JwksCache.java
│   └── TokenValidator.java
├── ratelimit/                ← Redis 토큰 버킷 로직
│   └── TokenBucketRateLimiter.java
└── config/                   ← Spring Cloud Gateway / Resilience4j 설정
    ├── GatewayConfig.java
    ├── SecurityConfig.java
    └── WebClientConfig.java
```

**presentation/application/domain/infrastructure 레이어 구분 없음**. 대신 **관심사 기반 패키지**(filter / route / security / ratelimit / config).

## Allowed Dependencies

- `filter/` → `security/`, `ratelimit/` (필터 체인이 인증·제한 로직을 호출)
- `filter/` → [libs/java-security](../../../libs/java-security) (JWT 검증 헬퍼), [libs/java-observability](../../../libs/java-observability) (MDC, metrics)
- `route/` → [libs/java-web](../../../libs/java-web) (공통 에러 포맷)
- `security/` → Redis (JWKS 캐시), WebClient (auth-service JWKS 엔드포인트 호출)
- `ratelimit/` → Redis (토큰 버킷)
- 모든 패키지 → `config/` (Spring Bean 주입 방향만)

## Forbidden Dependencies

- ❌ **MySQL 또는 JPA** — 게이트웨이는 영속 상태를 가지지 않는다
- ❌ **도메인 모델** (`com.example.auth.domain.*` 등 다른 서비스 도메인을 import) — 내부 HTTP를 통해서만 통신
- ❌ **비즈니스 validation** — 게이트웨이는 요청 *형식*만 검증하고 비즈니스 규칙은 다운스트림이 판단
- ❌ **동기 호출로 다운스트림 블로킹** — WebClient (reactive) 또는 명시적 타임아웃을 갖는 RestTemplate만
- ❌ **Redis 외 상태 저장소**

## Boundary Rules

### filter/
- Spring Cloud Gateway의 `GlobalFilter` 또는 `GatewayFilter` 구현
- 요청 개당 수행되는 작업만 포함 — 공유 상태 변경 금지(Redis 경유 카운터 제외)
- 응답 필터에서 원래 에러를 재포장할 때는 [platform/error-handling.md](../../../platform/error-handling.md)의 `{ code, message, timestamp, traceId }` 포맷 유지

### route/
- 업스트림 경로 정의와 ID 부여만 수행 — 라우트가 어떤 다운스트림에 매핑되는지는 여기서만 결정됨
- 라우트 추가는 반드시 [specs/contracts/http/gateway-api.md](../../contracts/http/) 업데이트와 동반

### security/
- JWT 서명 검증 + `exp`/`nbf` 확인만 수행. **권한(authorization)은 다운스트림이 판단**
- JWKS는 auth-service에서 10분 주기로 페치 + Redis 캐시 + kid 불일치 시 즉시 refetch

### ratelimit/
- 토큰 버킷 키 스키마: `rate:{scope}:{tenant_id}:{identifier}` (상세 패턴: `specs/services/gateway-service/redis-keys.md` 참조)
- 초과 시 429 응답 + `Retry-After` 헤더

### config/
- 보안·라우트·WebClient 타임아웃만 여기에 집중 — 비즈니스 파라미터 금지

## Integration Rules

- **HTTP 컨트랙트**: [specs/contracts/http/gateway-api.md](../../contracts/http/) — 공개 엔드포인트와 라우트 매핑
- **내부 컨트랙트**: [specs/contracts/http/internal/gateway-to-auth.md](../../contracts/http/internal/) — auth-service JWKS 엔드포인트 호출
- **다운스트림**: auth / account / admin / security-query 서비스로 라우팅. 다운스트림 응답은 그대로 전달 (에러 재가공 최소화)
- **퍼시스턴스**: 없음. Redis만 사용 (캐시·rate limit)
- **이벤트**: 발행·소비 모두 없음

### Admin Second-Layer Auth Delegation (Platform Invariant)

- `/api/admin/**` 및 `/.well-known/admin/**` 서브트리는 gateway 입장에서 **public-paths** 로 취급된다. gateway 는 라우팅·rate-limit·request-id·CORS 만 수행하고 operator JWT 검증을 하지 않는다.
- operator JWT 검증은 admin-service 의 `OperatorAuthenticationFilter` 에 위임되며, 이는 **플랫폼 불변식**이다. gateway 와 admin-service 가 동일 요청을 이중 검증하지 않는다.
- 이 위임 관계가 해소되는 변경(예: `OperatorAuthenticationFilter` deprecated, gateway 가 operator JWT 검증을 이관받음)은 본 파일과 [specs/contracts/http/gateway-api.md §Admin Routes](../../contracts/http/gateway-api.md) 의 개정이 **선행되어야** 한다. 코드 선행 변경은 금지.
- 상세 규약과 토큰 타입(`token_type="admin"` / `admin_bootstrap`)은 [specs/contracts/http/gateway-api.md §Admin Routes (second-layer auth)](../../contracts/http/gateway-api.md) 를 단일 원천으로 참조한다 (본 파일에서 중복 기술하지 않음).

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | 토큰 검증 로직, 토큰 버킷 수식, kid 매칭 | JUnit 5 + Mockito |
| Filter slice | 각 `GlobalFilter`의 요청·응답 변환 | `WebTestClient` |
| Integration | JWKS 페치 → Redis 캐시 → 만료 시 refetch / Rate limit 429 전이 | Testcontainers (Redis) + WireMock (auth-service) |
| Contract | 업스트림 라우트가 [specs/contracts/http/gateway-api.md](../../contracts/http/) 매핑과 일치 | 계약 테스트 (수작업 또는 Spring Cloud Contract) |

**필수 시나리오**: 만료 토큰 401 · 변조 토큰 401 · rate limit 초과 429 · 다운스트림 5xx 시 503 투명 전달 · JWKS rotation 무중단.

## Change Rule

1. 게이트웨이 동작(필터, 라우트, 인증 규칙) 변경은 먼저 이 파일과 [specs/contracts/http/gateway-api.md](../../contracts/http/)를 수정
2. 새로운 필터 추가는 해당 필터의 목적·실행 순서·실패 모드를 본 파일 `## filter/` 섹션에 기록
3. Rate limit 정책 변경은 [specs/features/rate-limiting.md](../../features/) 업데이트 동반
4. 업스트림 서비스가 추가되면 route/ 설정과 계약 파일을 같은 PR에서 갱신
