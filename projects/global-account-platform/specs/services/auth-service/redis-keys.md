# auth-service — Redis Keys

## 용도

로그인 실패 카운터, refresh token 블랙리스트. 모든 키는 TTL로 자동 만료.

---

## Key Schema

### Login Failure Counter

| 키 패턴 | `login:fail:{tenant_id}:{email_hash}` |
|---|---|
| **예시** | `login:fail:fan-platform:a1b2c3d4e5` (`{tenant_id}` + SHA256(email)[:10]) |
| **타입** | String (integer) |
| **값** | 연속 실패 횟수 |
| **TTL** | 900초 (15분) — 마지막 실패 기준 갱신 |
| **쓰기** | 로그인 실패 시 `INCR` + `EXPIRE 900`. 로그인 성공 시 `DEL` |
| **읽기** | 로그인 시도 전 GET → 임계치(기본 5회) 이상이면 즉시 429 (`LOGIN_RATE_LIMITED`) |

**`{tenant_id}` 세그먼트를 쓰는 이유**: 동일 이메일이 복수 테넌트에 등록 가능하므로
([architecture.md](architecture.md) `credentials.email` = `(tenant_id, email)` unique),
테넌트별로 실패 카운터를 격리해 한 테넌트의 무차별 시도가 다른 테넌트의 동일 이메일
사용자를 잠그지 않도록 한다 ([rules/traits/multi-tenant.md](../../../../../rules/traits/multi-tenant.md)
M1 — Redis key prefix `<tenant_id>:...` 강제). `{tenant_id}` 는 인증 대상 credential 의
해소된 테넌트(예: `fan-platform`, `wms`)이며, SUPER_ADMIN 의 platform-scope 센티넬
`'*'`(ADR-002 / `admin-service` Tenant Scope Enforcement) 은 이 키에 **사용되지 않는다** —
본 카운터는 admin operator 가 아니라 end-user 의 사전 인증(pre-auth) brute-force 가드이기 때문.

**`{email_hash}`를 쓰는 이유**: PII인 이메일을 Redis 키에 평문으로 노출하지 않기 위함 ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4). SHA256(email)의 prefix 10자 사용.

### Refresh Token Blacklist

| 키 패턴 | `refresh:blacklist:{tenant_id}:{jti}` |
|---|---|
| **예시** | `refresh:blacklist:fan-platform:550e8400-e29b-41d4-a716-446655440000` (`{tenant_id}` + refresh token `jti`) |
| **타입** | String (`"1"`) |
| **값** | 존재 여부만 의미 (값은 사실상 무관) |
| **TTL** | `expires_at - now` (토큰의 잔여 수명). 만료된 토큰은 블랙리스트할 필요 없음 |
| **쓰기** | 로그아웃, 강제 revoke, 토큰 재사용 탐지 시 `refresh:blacklist:{tenant_id}:{jti}` SET |
| **읽기** | `POST /api/auth/refresh` 시 `EXISTS` 검사. 존재하면 401 (`TOKEN_EXPIRED`/`SESSION_REVOKED`). 신규 키와 함께 레거시 키도 확인 (아래 레거시 fallback 참조) |

**`{tenant_id}` 세그먼트를 쓰는 이유**: refresh token 의 `tenant_id` claim 으로
테넌트별 블랙리스트를 격리한다 ([rules/traits/multi-tenant.md](../../../../../rules/traits/multi-tenant.md)
M1 — Redis key prefix `<tenant_id>:...` 강제). `{tenant_id}` 는 토큰에 이미
해소되어 들어있는 테넌트(예: `fan-platform`, `wms`)이며, refresh token 은
인증을 통과한 principal 에게만 발급되므로 `login:fail` 카운터와 달리 pre-auth
센티넬 `'*'`(ADR-002) 은 이 키에 **사용되지 않는다** — `{tenant_id}` 는 항상
구체 테넌트다.

**레거시 키 fallback (읽기 전용)**: TASK-BE-229 이전에 발급된 토큰은 테넌트
세그먼트가 없는 레거시 키 `refresh:blacklist:{jti}` 로 블랙리스트되었을 수 있다.
신규 쓰기는 **항상** `refresh:blacklist:{tenant_id}:{jti}` 형식만 사용하되,
읽기(`EXISTS`)는 신규 키와 레거시 `refresh:blacklist:{jti}` 키를 **모두** 확인하여
하위 호환을 보장한다 (코드: `RedisTokenBlacklist.buildKey` = 신규 쓰기/읽기,
`RedisTokenBlacklist.buildLegacyKey` = 레거시 읽기 전용). 레거시 키는 TTL 만료로
자연 소멸하므로 별도 마이그레이션은 불필요하다.

### 재사용 탐지 Bulk Invalidation

| 키 패턴 | `refresh:invalidate-all:{account_id}` |
|---|---|
| **예시** | `refresh:invalidate-all:user_12345` |
| **타입** | String (timestamp) |
| **값** | invalidation 발생 시각 (epoch millis) |
| **TTL** | refresh token 최대 수명 (기본 7일) |
| **쓰기** | `token.reuse.detected` 시 SET |
| **읽기** | refresh 시도 시 이 키가 존재하고 `issued_at < 이 값`이면 해당 토큰은 무효 |

---

## Failure Mode

| 장애 | 영향 | 대응 |
|---|---|---|
| Redis 전체 장애 | 실패 카운터 조회 불가 + 블랙리스트 조회 불가 | **fail-closed (보수적)**: 실패 카운터를 확인할 수 없으면 로그인 진행 허용하되 경고 메트릭 발행. 블랙리스트를 확인할 수 없으면 refresh 거부 (revoked 토큰 사용 위험 방지) |
| Redis 지연 | 로그인 응답 시간 증가 | 타임아웃 50ms 적용 |

---

## Naming Convention

- prefix: 기능 이름 (`login`, `refresh`)
- `:` separator
- 테넌트 격리 키는 기능 prefix 다음에 `{tenant_id}` 세그먼트 (`login:fail:{tenant_id}:...`, `refresh:blacklist:{tenant_id}:...`) — [rules/traits/multi-tenant.md](../../../../../rules/traits/multi-tenant.md) M1
- PII 값은 해시로 치환 (`email_hash`, account_id는 UUID이므로 그대로 사용)
- 영구 키 금지 — 모든 키에 TTL 필수
