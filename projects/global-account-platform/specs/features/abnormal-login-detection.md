# Feature: Abnormal Login Detection

## Purpose

로그인 이벤트 스트림을 실시간(near real-time) 분석하여 비정상 패턴을 탐지하고, 심각도에 따라 자동 잠금 또는 경고를 발행한다.

## Related Services

| Service | Role |
|---|---|
| security-service | 탐지 규칙 실행 소유. 이벤트 소비 → 규칙 평가 → suspicious 기록 → 자동 잠금 |
| auth-service | 원본 로그인 이벤트 발행 |
| account-service | 자동 잠금 명령 수신 (내부 HTTP) |
| admin-service | suspicious 이벤트 조회 (감사) |

## Detection Rules (Strategy Pattern)

각 규칙은 `SuspiciousActivityRule` 인터페이스를 구현. 독립적으로 평가되며 risk score를 산출.

### VelocityRule
- **조건**: 특정 계정의 시간당 로그인 실패 > 임계치 (기본 10회/시간)
- **데이터**: Redis `security:velocity:{account_id}:{window}` 카운터
- **Risk score**: `min(100, (failCount / threshold) * 80)`
- **액션**: score ≥ 80 → AUTO_LOCK

### GeoAnomalyRule
- **조건**: 성공 로그인의 지리적 위치가 이전 성공과 물리적으로 불가능한 거리 (예: 30분 내 한국→미국)
- **데이터**: Redis `security:geo:last:{account_id}` + MaxMind GeoIP
- **Risk score**: 거리·시간 비율 기반. 비행기 속도(900km/h) 초과 시 90+
- **액션**: score ≥ 85 → AUTO_LOCK

### DeviceChangeRule
- **조건**: 알려지지 않은 디바이스에서의 로그인 (최근 90일간 해당 device_fingerprint 미사용)
- **데이터**: Redis `security:device:seen:{account_id}` Set
- **Risk score**: 단독으로는 50 (다른 규칙과 조합 시 가중)
- **액션**: 단독으로는 ALERT (로그 + 메트릭). 다른 규칙과 복합 시 AUTO_LOCK

### TokenReuseRule
- **조건**: `auth.token.reuse.detected` 이벤트 수신
- **Risk score**: **100** (무조건)
- **액션**: **즉시 AUTO_LOCK** (최고 우선순위)

## Risk Score Aggregation

하나의 로그인 이벤트에 대해 여러 규칙이 동시에 발동할 수 있다. 집계 방식:

```
finalScore = max(rule1.score, rule2.score, ...)
```

max를 사용하는 이유: 하나라도 확신이 높은 규칙이 발동하면 즉시 대응. 평균은 심각한 탐지를 희석시킴.

## Action Thresholds

| Score 범위 | Action | 설명 |
|---|---|---|
| 0-49 | NONE | 기록만 |
| 50-79 | ALERT | `suspicious_events`에 기록 + 메트릭. 잠금 안 함 |
| 80-100 | AUTO_LOCK | `suspicious_events` 기록 + account-service에 자동 잠금 HTTP 호출 |

임계치는 `@ConfigurationProperties`로 조정 가능. 코드 하드코딩 금지.

## Auto-Lock Flow

1. 규칙 평가 → `finalScore ≥ 80`
2. `suspicious_events` 저장 (action_taken=AUTO_LOCK)
3. `security.suspicious.detected` 이벤트 발행 (outbox)
4. `POST /internal/accounts/{id}/lock` 호출 (Idempotency-Key = suspicious_event_id)
5. 성공 시 `security.auto.lock.triggered` 이벤트 발행 (lockRequestResult=SUCCESS)
6. 실패 시 3회 재시도 → 최종 실패 시 outbox에 pending 이벤트 + 운영자 알림

## Business Rules

- 탐지는 **비동기** — auth-service의 로그인 응답 시간에 영향 없음
- 자동 잠금은 **idempotent** — 같은 suspicious_event_id로 중복 호출 시 동일 결과
- 규칙 파라미터(임계치, 윈도우, 거리)는 **설정으로 주입**, 코드 변경 없이 튜닝 가능
- false positive 방지: DeviceChange 단독으로는 잠금하지 않음 (ALERT only)
- 모든 탐지 결과는 `suspicious_events`에 보존 (score < 50 제외 — NONE은 기록 안 함)

## Related Contracts

- Events: [auth-events.md](../contracts/events/auth-events.md) (소비), [security-events.md](../contracts/events/security-events.md) (발행)
- Internal: [security-to-account.md](../contracts/http/internal/security-to-account.md) (auto-lock)
- Query: [security-query-api.md](../contracts/http/security-query-api.md)
