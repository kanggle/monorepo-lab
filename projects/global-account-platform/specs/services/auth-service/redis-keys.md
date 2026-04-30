# auth-service — Redis Keys

## 용도

로그인 실패 카운터, refresh token 블랙리스트. 모든 키는 TTL로 자동 만료.

---

## Key Schema

### Login Failure Counter

| 키 패턴 | `login:fail:{email_hash}` |
|---|---|
| **예시** | `login:fail:a1b2c3d4e5` (SHA256(email)[:10]) |
| **타입** | String (integer) |
| **값** | 연속 실패 횟수 |
| **TTL** | 900초 (15분) — 마지막 실패 기준 갱신 |
| **쓰기** | 로그인 실패 시 `INCR` + `EXPIRE 900`. 로그인 성공 시 `DEL` |
| **읽기** | 로그인 시도 전 GET → 임계치(기본 5회) 이상이면 즉시 429 (`LOGIN_RATE_LIMITED`) |

**email_hash를 쓰는 이유**: PII인 이메일을 Redis 키에 평문으로 노출하지 않기 위함 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4). SHA256(email)의 prefix 10자 사용.

### Refresh Token Blacklist

| 키 패턴 | `refresh:blacklist:{jti}` |
|---|---|
| **예시** | `refresh:blacklist:550e8400-e29b-41d4-a716-446655440000` |
| **타입** | String (`"1"`) |
| **값** | 존재 여부만 의미 (값은 사실상 무관) |
| **TTL** | `expires_at - now` (토큰의 잔여 수명). 만료된 토큰은 블랙리스트할 필요 없음 |
| **쓰기** | 로그아웃, 강제 revoke, 토큰 재사용 탐지 시 SET |
| **읽기** | `POST /api/auth/refresh` 시 `EXISTS` 검사. 존재하면 401 (`TOKEN_EXPIRED`/`SESSION_REVOKED`) |

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
- PII 값은 해시로 치환 (`email_hash`, account_id는 UUID이므로 그대로 사용)
- 영구 키 금지 — 모든 키에 TTL 필수
