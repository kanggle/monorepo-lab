# gateway-service — Redis Keys

## 용도

gateway-service는 MySQL을 사용하지 않으며 Redis만 영속성 외 상태로 사용. 모든 키는 **만료·복원 가능한 휘발성** 상태.

---

## Key Schema

### JWKS Cache

| 키 | `jwks:cache` |
|---|---|
| **타입** | String (JSON) |
| **값** | auth-service `/internal/auth/jwks` 응답 전문 |
| **TTL** | 600초 (10분) |
| **쓰기** | JWKS 페치 성공 시 덮어쓰기 |
| **읽기** | JWT 검증 시마다 (`kid` 매칭) |
| **miss 시** | auth-service에 즉시 페치 요청. 실패 시 이전 값 유지 (in-memory grace 5분) |

### Rate Limit Token Bucket

| 키 패턴 | `rate:{scope}:{identifier}` |
|---|---|
| **예시** | `rate:login:192.168.1.0/24` (login scope, /24 서브넷) |
| **타입** | Hash (`count`, `last_reset`) |
| **TTL** | window 길이 + 10초 (안전 여유) |
| **쓰기** | 요청마다 INCR. 윈도우 초과 시 429 반환 |
| **읽기** | 요청마다 GET → 임계치 비교 |

**scope 종류**:

| scope | identifier | 윈도우 | 임계치 (기본값) | 설명 |
|---|---|---|---|---|
| `login` | IP /24 서브넷 해시 | 60초 | 20회 | 로그인 시도 rate limit |
| `signup` | IP | 60초 | 5회 | 가입 시도 rate limit |
| `refresh` | account_id | 60초 | 10회 | Token refresh rate limit |
| `global` | IP | 1초 | 100회 | 전체 API 과부하 방지 |

임계치는 환경 변수 또는 Redis config hash로 동적 조정 가능.

---

## Failure Mode

| 장애 | 영향 | 대응 |
|---|---|---|
| Redis 전체 장애 | JWKS 캐시 miss + rate limit 동작 불가 | JWKS: in-memory grace (5분). Rate limit: 정책에 따라 fail-open(트래픽 통과) 또는 fail-closed(전체 429). **기본값: fail-open** (서비스 가용성 우선, 단 `GatewayRedisDown` 알림 즉시 발행) |
| Redis 지연 (>100ms) | 요청 지연 추가 | 타임아웃 100ms 적용. 초과 시 캐시 miss로 처리 |

---

## Naming Convention

- prefix는 해당 서비스 이름이 아닌 **기능 이름** (`jwks`, `rate`)
- `:` separator
- 소문자 + snake_case
- TTL은 반드시 설정 (영구 키 금지)
