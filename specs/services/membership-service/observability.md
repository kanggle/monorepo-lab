# membership-service — Observability

## Metrics (Prometheus / Micrometer)

| 메트릭 | 타입 | 레이블 | 용도 |
|---|---|---|---|
| `membership_subscription_activated_total` | counter | `plan_level` | 구독 활성화 수 |
| `membership_subscription_expired_total` | counter | `plan_level` | 구독 만료 수 (스케줄러) |
| `membership_subscription_cancelled_total` | counter | `plan_level` | 구독 해지 수 |
| `membership_access_check_total` | counter | `result` (ALLOWED/DENIED), `plan_level` | 접근 체크 결과 |
| `membership_access_check_seconds` | histogram | — | 접근 체크 응답 latency |
| `membership_expiry_scheduler_processed_total` | counter | — | 스케줄러 1회 실행당 만료 처리 건수 |
| `membership_expiry_scheduler_seconds` | histogram | — | 스케줄러 실행 시간 |

엔드포인트: `/actuator/prometheus`

## Structured Logging

```json
{
  "timestamp": "2026-04-13T12:34:56.789Z",
  "level": "info",
  "message": "...",
  "traceId": "...",
  "spanId": "...",
  "requestId": "req-uuid",
  "accountId": "acc-uuid",
  "subscriptionId": "sub-uuid",
  "planLevel": "FAN_CLUB",
  "service": "membership-service"
}
```

- `accountId`: JWT sub claim 또는 내부 호출 파라미터
- **PII 금지**: 결제 정보(카드번호 등)를 로그에 절대 기록하지 않음

## Alerts

| Alert | Expression | Severity | Runbook |
|---|---|---|---|
| `MembershipExpirySchedulerStopped` | `increase(membership_expiry_scheduler_processed_total[1h]) == 0` | critical | `docs/runbooks/membership-scheduler.md` |
| `MembershipSubscriptionActivationFailHigh` | `rate(membership_subscription_activated_total[5m]) == 0 and rate(http_server_requests_total{uri="/api/membership/subscriptions",status="5.."}[5m]) > 0` | warning | `docs/runbooks/membership-activation.md` |

## Health Checks

`/actuator/health` — DB 연결, account-service circuit breaker 상태, 스케줄러 마지막 실행 시간

## Dashboards

`infra/grafana/dashboards/membership-overview.json` — 패널:
1. 구독 활성화/만료/해지 수 (시계열)
2. 플랜별 ACTIVE 구독 총계
3. 접근 체크 ALLOWED/DENIED 비율
4. 스케줄러 실행 시간 + 처리 건수
5. account-service circuit breaker 상태
