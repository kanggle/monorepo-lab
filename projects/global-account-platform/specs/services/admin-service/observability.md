# admin-service — Observability

기준: [platform/observability.md](../../../platform/observability.md)

---

## Metrics (Prometheus)

### 필수 메트릭

| 이름 | 타입 | 라벨 | 설명 |
|---|---|---|---|
| `admin_command_total` | counter | `action_code`, `outcome` (success/failure/denied) | 운영자 명령 수 |
| `admin_command_duration_seconds` | histogram | `action_code` | 명령 처리 시간 (downstream 호출 포함) |
| `admin_downstream_duration_seconds` | histogram | `target_service`, `endpoint` | downstream 내부 HTTP 호출 시간 |
| `admin_circuit_breaker_state` | gauge | `target_service` | 0=closed, 1=half_open, 2=open |
| `admin_audit_write_total` | counter | `result` (success/failure) | 감사 원장 기록 |
| `admin_audit_query_total` | counter | `query_type` | 감사 조회 횟수 (meta-audit 보조) |
| `admin_outbox_lag_seconds` | gauge | — | 미발행 outbox 이벤트 최대 나이 |
| `admin_auth_failure_total` | counter | `reason` (expired/invalid/insufficient_role) | 운영자 인증 실패 |

### 비즈니스 메트릭

| 이름 | 타입 | 설명 |
|---|---|---|
| `admin_lock_unlock_ratio` | gauge | 최근 1시간 lock / unlock 비율 (운영 건강 지표) |
| `admin_active_operators` | gauge | 최근 15분 내 활동한 unique operator 수 |

---

## Logs

### MDC 필드

| 필드 | 소스 |
|---|---|
| `traceId` | OTel / gateway 전파 |
| `requestId` | `X-Request-ID` |
| `operatorId` | JWT claim에서 추출 |
| `operatorRole` | JWT claim |
| `actionCode` | 실행 중인 admin command |
| `targetId` | 명령 대상 (accountId 등) |

### 로깅 규칙

- ✅ 모든 명령 시작·완료를 `INFO`로 기록: `operatorId` + `actionCode` + `targetId` + `outcome` + `duration_ms`
- ✅ 권한 부족 시 `WARN`: `operatorId` + `operatorRole` + `actionCode` + `denied_reason`
- ✅ downstream 실패 시 `ERROR`: `targetService` + `statusCode` + `retry_count`
- ❌ 감사 조회 결과의 PII → 로그 금지 (R4)
- ❌ `reason` 필드에 PII 포함 금지 (운영자가 입력하는 사유에도 PII 유효성 검증 적용)

---

## Traces (OTel)

| Span 이름 | 설명 |
|---|---|
| `admin.command.{action_code}` | 전체 명령 흐름 (root) |
| `admin.auth.verify` | 운영자 JWT 검증 |
| `admin.audit.begin` | 감사 기록 시작 (트랜잭션 내) |
| `admin.downstream.{target_service}` | 내부 HTTP 호출 |
| `admin.audit.complete` | 감사 기록 완료 |
| `admin.outbox.write` | Outbox 이벤트 저장 |
| `admin.query.{query_type}` | 감사 조회 (meta-audit span) |

---

## Alerts

| 이름 | 조건 | 심각도 | 대응 |
|---|---|---|---|
| `AdminCommandFailureHigh` | failure 비율 > 20% (10분) | critical | downstream 서비스 상태 점검 |
| `AdminAuditWriteFailing` | audit_write failure > 0 | critical | **fail-closed (A10)**: DB 점검, 모든 명령이 차단됨 |
| `AdminDownstreamCircuitOpen` | 어떤 downstream이든 circuit open | critical | 해당 서비스 상태 점검 |
| `AdminAuthFailureSpike` | 인증 실패 > 10회/분 | warning | 잘못된 credential 또는 공격 시도 |
| `AdminOutboxLagHigh` | outbox_lag > 60초 | warning | Kafka / relay 점검 |
| `AdminUnusualLockSurge` | lock 명령 > 20/시간 | warning | 정상 운영 vs 실수 확인 |

---

## Dashboard (Grafana)

1. **Command Rate** — by action_code, stacked
2. **Command Outcome** — success / failure / denied pie chart
3. **Command Latency** — p50/p95/p99 by action_code
4. **Downstream Health** — per-target duration + circuit breaker state
5. **Operator Activity** — active operators gauge + unique per day
6. **Audit Write Health** — success / failure (failure = emergency)
7. **Auth Failures** — expired / invalid / insufficient_role
8. **Lock/Unlock Ratio** — trend line
9. **Outbox Lag** — gauge + threshold
