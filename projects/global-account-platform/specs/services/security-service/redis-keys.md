# security-service — Redis Keys

## 용도

이벤트 소비 멱등성(dedup), 탐지 규칙의 시간 윈도우 카운터, 마지막 로그인 지리·디바이스 정보 캐시.

---

## Key Schema

### Event Dedup

| 키 패턴 | `security:event-dedup:{eventId}` |
|---|---|
| **예시** | `security:event-dedup:550e8400-e29b-41d4-a716-446655440000` |
| **타입** | String (`"1"`) |
| **값** | 존재 여부만 의미 |
| **TTL** | 86400초 (24시간) |
| **쓰기** | 이벤트 처리 완료 직후 SET |
| **읽기** | 이벤트 소비 시작 시 `EXISTS` 검사. 존재하면 skip (dedup hit) |
| **이중 방어** | Redis miss 시 MySQL `processed_events` 테이블로 fallback ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T8) |

### Velocity Counter (VelocityRule)

| 키 패턴 | `security:velocity:{account_id}:{window}` |
|---|---|
| **예시** | `security:velocity:user_12345:3600` (1시간 윈도우) |
| **타입** | String (integer) |
| **값** | 윈도우 내 실패 횟수 |
| **TTL** | window 길이 + 60초 |
| **쓰기** | `auth.login.failed` 소비 시 `INCR` + `EXPIRE` |
| **읽기** | 소비 시 GET → 임계치(기본 10회/시간) 이상이면 suspicious 탐지 |

### Last Known Geo

| 키 패턴 | `security:geo:last:{account_id}` |
|---|---|
| **예시** | `security:geo:last:user_12345` |
| **타입** | Hash (`country`, `region`, `lat`, `lon`, `occurred_at`) |
| **값** | 마지막 성공 로그인의 지리 정보 |
| **TTL** | 2592000초 (30일) |
| **쓰기** | `auth.login.succeeded` 소비 시 덮어쓰기 |
| **읽기** | 새 로그인 성공 시 이전 geo와 비교 → 거리 임계치 초과 시 GeoAnomalyRule 발동 |

### Known Devices

| 키 패턴 | `security:device:seen:{account_id}` |
|---|---|
| **예시** | `security:device:seen:user_12345` |
| **타입** | Set (device_fingerprint 해시 목록) |
| **값** | 이 계정에서 과거에 사용된 디바이스 fingerprint 집합 |
| **TTL** | 7776000초 (90일) |
| **쓰기** | 성공 로그인 시 `SADD` |
| **읽기** | 새 로그인 시 `SISMEMBER` → 미등록 디바이스이면 DeviceChangeRule 평가 |

---

## Failure Mode

| 장애 | 영향 | 대응 |
|---|---|---|
| Redis 전체 장애 | dedup 빠른 경로 miss → MySQL fallback으로 전환 (느리지만 정확). velocity/geo/device 캐시 miss → 탐지 규칙이 평가 불가 | **dedup은 MySQL로 graceful degradation**. 탐지 규칙은 데이터 부재 시 "판단 보류"로 처리 (false positive 방지) + 경고 메트릭 `security_redis_degraded` 발행 |
| Redis 지연 (>100ms) | 이벤트 소비 처리 시간 증가 | 타임아웃 100ms. 초과 시 Redis skip + MySQL fallback |

---

## Naming Convention

- prefix: `security:` (서비스 소유 명시)
- 기능 이름: `event-dedup`, `velocity`, `geo`, `device`
- `:` separator
- account_id는 UUID이므로 그대로 사용 (PII 아님)
- eventId는 UUID 그대로 사용
- 영구 키 금지 — 모든 키에 TTL 필수
