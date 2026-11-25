# security-service — Observability

기준: [platform/observability.md](../../../../../platform/observability.md)

---

## Metrics (Prometheus)

### 핵심 메트릭

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| 이름 | 타입 | 라벨 | 설명 |
|---|---|---|---|
| `security_events_consumed_total` | counter | `topic`, `outcome` (processed/dedup/error) | 소비된 이벤트 수 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `kafka_consumer_lag` | gauge | `topic`, `group`, `partition` | Kafka consumer lag (파티션별, `SecurityMetricsConfig` 의 `kafka.consumer.lag`) |
| `security_event_processing_duration_seconds` | histogram | `topic` | 이벤트 처리 시간 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_dlq_total` | counter | `topic` | DLQ로 이관된 이벤트 수 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_dlq_depth` | gauge | `topic` | DLQ에 쌓인 이벤트 수 |
| `security_dedup_hit_total` | counter | — | 중복 이벤트 스킵 횟수 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_suspicious_detected_total` | counter | `rule_code` | 탐지된 의심 이벤트 (규칙별) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_auto_lock_issued_total` | counter | `result` (success/failure) | 자동 잠금 명령 발행 |
| `security_account_service_duration_seconds` | histogram | — | auto-lock HTTP 호출 시간 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_login_history_write_total` | counter | `outcome` | append-only 기록 결과 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_outbox_lag_seconds` | gauge | — | 미발행 outbox 이벤트 최대 나이 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `kafka.consumer.lag` | gauge | `topic`, `group`, `partition` | 파티션별 consumer lag(레코드 수). `SecurityMetricsConfig` 가 10초마다 Kafka 클라이언트 `records-lag` 를 읽어 갱신하고, 리밸런스로 사라진 파티션의 게이지는 제거. (위 `security_consumer_lag` 와 별개로 코드에 등록된 이름) |
| `security_auto_lock_retries_total` | counter | — | account-service `/lock` 호출 재시도 횟수 (5xx/예외 후 다음 시도 전에 증가) |
| `security_auto_lock_failures_total` | counter | — | account-service `/lock` 호출 최종 실패 횟수 (재시도 소진 또는 409 외 4xx). 계정이 잠기지 않았으므로 운영자 개입 필요 — alert 대상 (`SecurityAutoLockFailing`) |

### 비즈니스 메트릭

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| 이름 | 타입 | 설명 |
|---|---|---|
| `security_risk_score_distribution` | histogram | 탐지된 suspicious event의 risk score 분포 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security_login_history_total` | counter | `outcome` (SUCCESS/FAILURE/RATE_LIMITED/TOKEN_REUSE) | 로그인 이력 유형별 누적 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |

---

## Logs

### MDC 필드

| 필드 | 소스 |
|---|---|
| `traceId` | Kafka 헤더 `traceparent`에서 복원 |
| `eventId` | 소비된 이벤트의 UUID |
| `accountId` | 이벤트 payload |
| `topic` | Kafka 토픽 |
| `ruleCode` | 탐지 규칙 (suspicious 평가 시) |

### 로깅 규칙

- ❌ IP 전문 / 이메일 / 토큰 → 로그 금지 (R4). IP는 마스킹된 형태만
- ✅ `eventId`, `accountId`, `topic`, `outcome`, `processing_ms` — INFO
- ✅ 탐지 시 `WARN` + `ruleCode` + `risk_score` + `action_taken`
- ✅ DLQ 이관 시 `ERROR` + `eventId` + `topic` + `failure_reason`

---

## Traces (OTel)

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Span 이름 | 설명 |
|---|---|
| `security.consume.{topic}` | 이벤트 소비 전체 흐름 |
| `security.dedup.check` | Redis dedup 조회 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security.history.write` | login_history append — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security.detect.{rule_code}` | 개별 탐지 규칙 평가 |
| `security.auto-lock.issue` | account-service 잠금 HTTP 호출 |
| `security.outbox.write` | Outbox 이벤트 저장 — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `security.query.login-history` | 조회 HTTP 요청 처리 |

Kafka 헤더 `traceparent`를 span parent로 연결 → auth-service 로그인 trace와 이어지는 분산 트레이스.

---

## Alerts

| 이름 | 조건 | 심각도 | 대응 |
|---|---|---|---|
| `SecurityConsumerLagHigh` | lag > 10000 (any partition, 5분 유지) | critical | consumer 처리량 부족, scale-out 또는 원인 조사 |
| `SecurityDlqDepthNonZero` | dlq_depth > 0 | warning | DLQ 내용 수동 검토 + 재처리 |
| `SecurityDlqDepthCritical` | dlq_depth > 100 | critical | 소비 로직 버그 또는 infrastructure 장애 |
| `SecurityAutoLockFailing` | auto_lock failure > 0 (10분) | critical | account-service 연결 점검 |
| `SecuritySuspiciousSurge` | suspicious_detected > 50/시간 | warning | 대규모 공격 또는 규칙 오탐(false positive) 확인 |
| `SecurityOutboxLagHigh` | outbox_lag > 60초 | warning | Kafka / relay 점검 |

---

## Dashboard (Grafana)

1. **Consumer Throughput** — events/sec by topic
2. **Consumer Lag** — per topic, per partition
3. **DLQ Depth** — gauge per topic, zero is healthy
4. **Event Processing Latency** — histogram by topic
5. **Dedup Hit Rate** — dedup / total ratio
6. **Detection Results** — suspicious by rule_code (stacked bar)
7. **Risk Score Distribution** — histogram
8. **Auto-Lock Commands** — success / failure rate
9. **Login History by Outcome** — SUCCESS / FAILURE / TOKEN_REUSE
10. **Outbox Lag** — gauge + threshold
