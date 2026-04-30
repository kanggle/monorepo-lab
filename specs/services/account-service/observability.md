# account-service — Observability

기준: [platform/observability.md](../../../platform/observability.md)

---

## Metrics (Prometheus)

### 필수 메트릭

| 이름 | 타입 | 라벨 | 설명 |
|---|---|---|---|
| `account_signup_total` | counter | `result` (success/duplicate/validation_error) | 가입 시도 결과 |
| `account_signup_duration_seconds` | histogram | — | 가입 처리 시간 |
| `account_status_transition_total` | counter | `from`, `to`, `reason` | 상태 전이 횟수 |
| `account_status_transition_rejected_total` | counter | `from`, `to` | 불허 전이 시도 |
| `account_profile_update_total` | counter | `result` (success/not_found/conflict) | 프로필 변경 |
| `account_deletion_requested_total` | counter | `reason` (user_request/admin/regulated) | 삭제 요청 수 |
| `account_anonymization_total` | counter | `result` (success/failure) | 유예 만료 후 익명화 실행 |
| `account_outbox_lag_seconds` | gauge | — | 미발행 outbox 이벤트 최대 나이 |
| `account_internal_api_duration_seconds` | histogram | `caller`, `endpoint` | 내부 HTTP 요청 처리 시간 |

### 비즈니스 메트릭

| 이름 | 타입 | 설명 |
|---|---|---|
| `account_total_by_status` | gauge | 상태별 계정 수 (ACTIVE/LOCKED/DORMANT/DELETED) |
| `account_dormant_candidates` | gauge | 휴면 전이 대상 계정 수 (365일 미접속) |

---

## Logs

### MDC 필드

| 필드 | 소스 |
|---|---|
| `traceId` | OTel / gateway 전파 |
| `requestId` | `X-Request-ID` |
| `accountId` | 대상 계정 |
| `action` | `signup` / `update_profile` / `change_status` / `delete` |
| `actorType` | `user` / `operator` / `system` |

### 로깅 규칙

- ❌ 이메일 / 전화 / 생년월일 / display name 평문 → 로그 금지 (R4)
- ✅ `accountId`, `action`, `actorType`, `result`, `statusTransition` (from→to) 만 INFO
- ✅ 상태 전이 시 `INFO` + `accountId` + `from` + `to` + `reason_code` + `actor_type`
- ✅ 불허 전이 시 `WARN` + 동일 필드 (이상 탐지 보조)

---

## Traces (OTel)

| Span 이름 | 설명 |
|---|---|
| `account.signup` | 전체 가입 흐름 |
| `account.profile.update` | 프로필 변경 |
| `account.status.change` | 상태 전이 (state machine 경유) |
| `account.delete.request` | 삭제 요청 (유예 진입) |
| `account.anonymize` | PII 익명화 실행 |
| `account.internal.credential-lookup` | auth-service 요청 처리 |
| `account.internal.lock` | security/admin 잠금 요청 처리 |
| `account.outbox.write` | Outbox 이벤트 저장 |

---

## Alerts

| 이름 | 조건 | 심각도 | 대응 |
|---|---|---|---|
| `AccountSignupFailureHigh` | duplicate + validation_error > 50% (5분) | warning | 봇 가입 시도 가능성 |
| `AccountStatusTransitionRejectedSpike` | rejected 전이 > 10회/분 | warning | 비정상 호출 패턴 조사 |
| `AccountOutboxLagHigh` | `account_outbox_lag_seconds` > 60 | warning | Kafka / relay 점검 |
| `AccountAnonymizationFailing` | anonymization failure > 0 | critical | PII 보존 기간 초과 위험 (R7) |
| `AccountLockedSurge` | ACTIVE→LOCKED 전이 > 50회/시간 | critical | 대규모 credential stuffing 또는 잘못된 자동 잠금 규칙 |

---

## Dashboard (Grafana)

1. **Signup Rate** — success / duplicate / validation_error
2. **Status Distribution** — gauge by ACTIVE / LOCKED / DORMANT / DELETED
3. **Status Transitions** — sankey 또는 stacked bar (from → to)
4. **Internal API Performance** — duration by caller (auth / security / admin)
5. **Outbox Lag** — gauge + threshold
6. **Anonymization Queue** — pending deletions + completion rate
7. **Dormant Candidates** — 휴면 전이 대상 추이
