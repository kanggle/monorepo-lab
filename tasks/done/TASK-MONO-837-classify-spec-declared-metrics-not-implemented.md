# Task ID

TASK-MONO-837

# Title

스펙이 선언한 metric · span 이름 중 코드에 없는 것을 전수 분류한다 — 무엇을 구현하고 무엇을 스펙에서 고칠지 소유자가 고를 수 있게(분류만, 구현 · 스펙 수정은 하지 않는다)

# Status

done

# Owner

monorepo

# Task Tags

- observability
- analysis

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (행마다 판단 — 프레임워크 이름 대응 · 파생값 · 이름 드리프트 · 오탐 제거)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-836` · `TASK-BE-665` 인계 «남은 것» → 소유자 «2번 진행».

# Dependency Markers

- 선행: `TASK-MONO-833`(코드 → 스펙 방향: 등록된 이름은 전부 문서에 있다, 가드 `check-metric-name-registry.sh`) · `TASK-MONO-834`(알림 → 코드 방향, 가드 `check-alert-rule-metrics.sh`). 이 티켓은 남은 방향 **스펙 → 코드** 를 잰다. 셋째 방향엔 가드가 없다.
- 후속: 소유자 결정 뒤의 구현 · 스펙 수정 티켓(이 티켓의 산출물이 그 입력).

# Goal

`TASK-BE-662` · `664` · `665` 는 스펙이 «필수» 라고 적은 metric 이 코드에 없어서 알림이 no-data 였던 결함들이었다. 그 셋은 **알림이 묻던 것** 만 고쳤다. 스펙에는 아직 코드에 없는 이름이 많이 남아 있고, 그중 일부는 결함(대시보드 패널이 비어 있다)이고 일부는 스펙 쪽이 틀렸다(프레임워크가 다른 이름으로 이미 낸다 · 쿼리로 계산하는 파생값 · 폐기된 서비스). 어느 쪽인지 행마다 갈라 두지 않으면 «전부 구현» 도 «전부 삭제» 도 틀린다.

기안 시점 예비 측정(2026-11-25 UTC, 아래 방법): 스펙 표 행에서 뽑은 고유 이름 251개(metric 228 · span 23, 4 프로젝트) 중 코드에 meter 로 등록된 것 74.

# Scope

## In Scope

1. **모집단**: `projects/*/specs/**/*.md` 의 표 행 중, 둘러싼 제목(가장 안쪽 분류 제목 기준)이 metric/메트릭/observability/관측 → metric, span/trace/tracing/추적(단어 경계) → span. 로그 필드 표(log/logging/로그/MDC 제목)는 제외. 행의 **첫 칸** 의 백틱 토큰 중 소문자 점·밑줄 이름. (project, kind, name) 로 중복 제거.
2. **코드 대조**: 프로젝트 `apps/*/src/main` + `libs/*/src/main` 의 `.java`/`.kt`. «등록됨» = meter 등록 호출(`Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer.builder(` · `.counter(` · `.timer(` · `.gauge(` · `.summary(`)에 넘어간 리터럴 또는 상수 값. 비교는 `.`/`-`→`_` 정규화 + 스펙 이름에서 Prometheus 접미사(`_total` · `_count` · `_sum` · `_bucket` · `_max`, 그다음 `_seconds`) 제거. **meter 가 아닌 리터럴(토픽 · 이벤트 · 태그 값)과의 일치는 구현으로 치지 않는다**(`TASK-MONO-835` 의 토픽 이름 오탐).
3. **분류**(행마다 하나): A 구현됨 · A2 이름만 다름(같은 계측이 다른 이름으로 등록) · B 프레임워크가 다른 이름으로 낸다(확인된 것) · B? 프레임워크 추정(미확인 — 확인 방법 명시) · C 파생값(스펙이 스스로 «계산» 이라 적음) · D1 미구현 + 대시보드/알림이 묻는다 · D2 미구현 · E span · F 폐기 서비스 스펙.
4. **곁가지 측정**: D 이름이 `projects/*/infra/**`(Grafana 대시보드 · 알림)에 나오는지.
5. **산출물**: 이 티켓 증거 절에 범주 × 프로젝트 표 · 범주별 이름 목록(서비스별) · 범주별 **소유자 결정 선택지와 에이전트 추천**. 측정 스크립트 원문은 증거 절 끝에(재현용).

## Out of Scope

- 구현 · 스펙 수정 · 대시보드 수정 — 전부 소유자 결정 뒤 별 티켓.
- 셋째 방향 가드 신설 — 결정 뒤 판단(분류가 끝나기 전엔 무엇을 «위반» 으로 칠지 정할 수 없다).

# Acceptance Criteria

- [x] AC-1: 모집단 수와 그 방법이 증거에 있다. 방법의 **오탐 두 종**(제목 패턴이 «Contract» 를 «trac» 로 잡음 · 토픽 리터럴 일치)을 어떻게 걸렀는지 적는다.
- [x] AC-2: 모든 고유 이름이 정확히 한 범주에 있다(범주 합 = 모집단).
- [x] AC-3: B 는 행마다 근거(어느 프레임워크 시리즈 · 왜 존재한다고 보는지 — 실측 또는 코드 근거), B? 는 확인 방법.
- [x] AC-4: D1 은 이름마다 묻는 파일.
- [x] AC-5: 범주별 소유자 결정 선택지 + 추천(모델 표기 포함).

# Related Specs

- `platform/observability.md` § Business Metrics · § Change Rule
- 각 프로젝트 `specs/services/*/observability.md` · `idempotency.md` · `external-integrations.md` · `sagas/*.md`

# Related Contracts

- 없음.

# Edge Cases

- 같은 이름이 여러 스펙 파일(계약 + 서비스)에 있다 → 한 행, 파일 목록.
- 이름이 프로젝트 간에 같다(`auth_login_total` — iam 구현, ecommerce 폐기 스펙) → 프로젝트별로 따로.
- 등록이 헬퍼를 거친다(`buildCounter("…")`) → 등록 호출 정규식에 안 걸린다. 리터럴만 일치하는 행은 손으로 연다.

# Failure Scenarios

- 분류기가 토픽 이름을 구현으로 셈 → D 가 줄어 보이고 그 결함이 다시 숨는다. AC-1 이 두 오탐 처리를 요구한다.
- «추정» 을 «확인» 으로 적음 → 스펙을 틀린 이름으로 고치게 된다. B / B? 분리.

# Test Requirements

- 범주 합 = 모집단(스크립트 출력) · 리터럴만 일치한 행 전부 손 확인.

# Implementation Evidence (2026-11-25 UTC)

## 결과 — 범주 × 프로젝트 (고유 이름, AC-2: 합 = 모집단 251)

| 범주 | ecommerce | fan | iam | wms | 합 |
|---|---:|---:|---:|---:|---:|
| A 구현됨(그 이름으로 meter 등록) | 37 | 9 | 15 | 13 | 74 |
| A2 이름만 다름(같은 계측이 다른 이름으로 등록) | 0 | 1 | 1 | 1 | 3 |
| B 프레임워크가 다른 이름으로 낸다 | 0 | 1 | 4 | 1 | 6 |
| C 파생값(스펙이 스스로 «계산» · 별칭이라 적음) | 0 | 0 | 1 | 11 | 12 |
| D1 미구현 + 대시보드가 묻는다(패널이 빈다) | 1 | 0 | 5 | 0 | 6 |
| D2 미구현 | 30 | 4 | 36 | 50 | 120 |
| E span(수동 span 생성 코드 0) | 0 | 0 | 23 | 0 | 23 |
| F 폐기 서비스 스펙(`auth-service-deprecated`) | 7 | 0 | 0 | 0 | 7 |
| **합** | 75 | 15 | 85 | 76 | **251** |

erp · finance · scm · platform-console 은 스펙에 metric 표가 없어(모집단 0) 표에 없다. 예비 측정(기안)의 «등록 74» 는 그대로다.

## AC-1 — 모집단과 방법, 그리고 걸러낸 오탐

방법은 Scope 1·2 그대로(스크립트 원문은 맨 끝). 첫 실행에서 두 오탐이 나왔고 둘 다 결과를 크게 바꿨다:

1. **제목 패턴 «trac» 이 «Contract» 를 잡았다** — span 으로 분류된 240행 중 217행이 이벤트 · HTTP 계약 문서의 토픽 · 경로 표였다(진짜 span 표는 iam `observability.md` 의 `Traces (OTel)` 5곳 23행뿐). 단어 경계(`\bspans?\b` · `\btraces?\b` · `\btracing\b`)로 고친 뒤 395행 → 274행(중복 제거 전). 🔴 고치는 중에 스크립트 안의 `\b` 가 셸 → 파이썬 문자열을 두 번 거치며 **백스페이스(`\x08`)** 로 바뀌어 span 이 0건이 되는 두 번째 사고가 있었다 — `repr()` 로 그 줄을 찍어 발견했다.
2. **meter 가 아닌 리터럴과의 일치** — 처음 판정은 «문자열 리터럴 어딘가에 있으면 구현» 이었다. `security_suspicious_detected_total` 은 토픽 `"security.suspicious.detected"` 와, ecommerce `user_withdrawn_total` 은 태그 값 `"user_withdrawn"` 과 일치해 «구현» 으로 셌다(`TASK-MONO-835` 와 같은 오탐). 판정을 «meter 등록 호출에 넘어간 리터럴 또는 상수 값» 으로 좁혔다. 좁힌 뒤 «리터럴만 일치» 로 남은 11행은 전부 손으로 열었다: 토픽 1 · 태그 값 1 → D2, `http.server.requests` 2 → B(프레임워크), ecommerce `auth-service-deprecated` 7 → 헬퍼 `buildCounter("…")` 로 **실제 등록됨**(정규식이 헬퍼를 못 봄) → F.

로그 필드 표(`Logs` · `7.2 Logs` · MDC)는 `Observability` 제목 아래 있어 metric 으로 들어왔다 → 제외 규칙 추가. 최종 274행 → (project, kind, name) 중복 제거 251. 등록된 meter 이름(코드 쪽 모집단): ecommerce 55 · iam 34 · wms 37 · fan 21.

`difflib` 유사도 ≥ 0.8 로 뽑은 «이름 드리프트 후보» 17개는 대부분 다른 개념이었다(`promotion_created_total` ↔ `product_created_total` 등) — 손으로 연 뒤 3개만 A2, 1개(`outbound.idempotency.mismatch.count`)는 스펙이 스스로 «(alias) `lookup.count{result=conflict}`» 라 적어 C.

## A2 — 이름만 다름 (3)

| 프로젝트 · 서비스 | 스펙 이름 | 코드가 등록한 이름 | 근거 |
|---|---|---|---|
| fan `artist-service` | `outbox_pending_count` | `artist.outbox.pending.count` | `ArtistOutboxPublisher.java:76` `Gauge.builder` |
| iam `security-service` | `security_consumer_lag` | `kafka.consumer.lag`(파티션별) | `SecurityMetricsConfig.java:151` `Gauge.builder` |
| wms `notification-service` | `wms.notification.delivery.attempt` | `notification.delivery.attempts` | 같은 서비스 `external-integrations.md` 는 코드 이름으로 적었다 — 스펙 두 파일이 서로 다르다 |

🔵 A2 의심(확인 안 함, D2 에 둠): wms `wms.notification.dedupe.outcome{outcome}`(`idempotency.md:185`) ↔ 등록된 `notification.routing.dedupe{outcome}`(`architecture.md:371`, `AlertRoutingService`) — 이름이 다르고 outcome 값 집합도 다르게 적혀 있어 같은 계측인지 코드로 확인이 필요하다.

## B — 프레임워크가 다른 이름으로 낸다 (6, AC-3)

| 프로젝트 · 서비스 | 스펙 이름 | 실제 시리즈 | 근거 수준 |
|---|---|---|---|
| fan `community` · `membership` | `http.server.requests` | `http_server_requests_seconds_*` | Spring Boot 내장. 버킷은 `TASK-MONO-836` 테스트에서 **실측** |
| iam `auth-service` | `auth_circuit_breaker_state{target}` | `resilience4j_circuitbreaker_state{name="accountService"}` | **실측** — `TASK-BE-665` 테스트 scrape. 나머지 독립 차단기 3개는 지표 없음 |
| iam `admin-service` | `admin_circuit_breaker_state` | `resilience4j_circuitbreaker_state{name="accountService"}` | 코드 · 클래스패스 — `@CircuitBreaker(name = "accountService")`(`AccountServiceClient.java:45`) + 런타임 클래스패스 `resilience4j-spring-boot3` · `resilience4j-micrometer`. scrape 미실측 |
| wms `notification-service` | `notification.channel.circuit.state` | `resilience4j_circuitbreaker_state{name="slack"}` | 코드 · 클래스패스 — `@CircuitBreaker(name = "slack")`(`SlackChannelAdapter.java:71`) + 같은 두 모듈. scrape 미실측 |
| iam `gateway-service` | `gateway_requests_total` | `http_server_requests_seconds_count`(WebFlux 서버) | Boot 내장. ⚠️ `routeId` 라벨은 없다 — `spring_cloud_gateway_requests_*` 는 SCG 4.2.0 메타데이터상 `spring.cloud.gateway.metrics.enabled` 기본 **false** 이고 iam 은 켜지 않는다 |
| iam `gateway-service` | `gateway_request_duration_seconds` | `http_server_requests_seconds_bucket` | 위와 같음 · 버킷은 836 뒤 |

🔴 `gateway_upstream_duration_seconds` 는 B 가 아니다 — upstream 시간은 SCG 자체 metrics(꺼짐)만 낸다 → D2. `gateway_circuit_breaker_state` 도 D2 — iam gateway 에는 차단기가 **아예 없다**(`@CircuitBreaker` · `CircuitBreakerRegistry` · `CircuitBreaker.of` 0건).

## D1 — 미구현 + 대시보드가 묻는다 (6, AC-4)

| 프로젝트 · 서비스 | 이름 | 묻는 파일 |
|---|---|---|
| iam `account-service` | `account_signup_total` | `iam/infra/grafana/dashboards/account-overview.json` |
| iam `admin-service` | `admin_audit_write_total` · `admin_audit_query_total` | `iam/infra/grafana/dashboards/admin-overview.json` |
| iam `auth-service` | `auth_token_issued_total` · `auth_token_refresh_total` | `iam/infra/grafana/dashboards/auth-overview.json` |
| ecommerce `payment-service` | `payment_failed_total` | `ecommerce/infra/grafana/dashboards/business-metrics.json` |

같은 iam 이름이 `auth_circuit_breaker_state` 와 함께 `auth-overview.json` 에 있다(그 패널도 빈다 — B 의 실제 이름으로 바꿔야 한다). 알림 규칙에는 D 이름이 0개(`check-alert-rule-metrics.sh` 가 막는다).

🆕 **곁가지 결함**: wms 의 Grafana 대시보드 중 `account-overview.json` · `admin-overview.json` 은 iam 것과 **바이트 단위로 같고**, `auth-overview.json` 은 diff ±4줄 차이다 — wms 에 없는 서비스(auth · account)의 iam 지표를 묻는다. `TASK-MONO-834` 가 고친 «wms 알림 = iam 복사본» 과 같은 결함이 대시보드에 남아 있다(9개 파일 전부 같은 이름으로 존재, 최종 변경 `a45707894` 2026-04-18 재구성 커밋).

## C · D2 · E · F — 이름 목록

#### C 파생값 (12)

- iam `admin-service` (1): `admin_lock_unlock_ratio`
- wms `admin-service` (1): `admin.idempotency.hit.rate`
- wms `contracts,inbound-service` (1): `inbound.webhook.dedupe.hit.rate`
- wms `contracts,outbound-service` (1): `outbound.webhook.dedupe.hit.rate`
- wms `inbound-service` (2): `inbound.event.dedupe.hit.rate` · `inbound.idempotency.hit.rate`
- wms `inventory-service` (2): `inventory.event.dedupe.hit.rate` · `inventory.idempotency.hit.rate`
- wms `notification-service` (1): `notification.event.dedupe.hit.rate`
- wms `outbound-service` (3): `outbound.event.dedupe.hit.rate` · `outbound.idempotency.hit.rate` · `outbound.idempotency.mismatch.count`

#### D2 미구현 (120)

- ecommerce `batch-worker` (4): `batch_expired_sessions_cleaned_total` · `batch_job_duration_seconds` · `batch_job_failure_total` · `batch_job_total`
- ecommerce `notification-service` (3): `event_consumed_total` · `event_duplicate_total` · `notification_retry_total`
- ecommerce `promotion-service` (5): `coupon_expired_total` · `coupon_issued_total` · `coupon_restored_total` · `coupon_used_total` · `promotion_created_total`
- ecommerce `review-service` (4): `review_created_total` · `review_deleted_total` · `review_purchase_verification_total` · `review_updated_total`
- ecommerce `settlement-service` (5): `settlement_accrual_total` · `settlement_duplicate_event_total` · `settlement_reversal_total` · `settlement_snapshot_missing_total` · `settlement_snapshot_recorded_total`
- ecommerce `shipping-service` (4): `shipping.webhook.received` · `shipping_created_total` · `shipping_delivered_total` · `shipping_status_transition_total`
- ecommerce `user-service` (5): `user_address_created_total` · `user_address_deleted_total` · `user_profile_created_total` · `user_profile_updated_total` · `user_withdrawn_total`
- fan `artist-service` (1): `outbox_lag_seconds`
- fan `membership-service` (3): `membership_access_check_fail_closed_total` · `membership_access_check_total` · `membership_payment_declined_total`
- iam `account-service` (9): `account_anonymization_total` · `account_deletion_requested_total` · `account_dormant_candidates` · `account_internal_api_duration_seconds` · `account_profile_update_total` · `account_signup_duration_seconds` · `account_status_transition_rejected_total` · `account_status_transition_total` · `account_total_by_status`
- iam `admin-service` (7): `admin_active_operators` · `admin_active_partnerships` · `admin_auth_failure_total` · `admin_command_duration_seconds` · `admin_downstream_duration_seconds` · `admin_outbox_lag_seconds` · `admin_partnership_cascade_participants`
- iam `auth-service` (6): `auth_account_service_duration_seconds` · `auth_active_sessions_total` · `auth_credential_verify_duration_seconds` · `auth_login_duration_seconds` · `auth_login_failure_streak_max` · `auth_redis_failure_counter_ops_total`
- iam `gateway-service` (4): `gateway_circuit_breaker_state` · `gateway_jwks_cache_age_seconds` · `gateway_jwks_fetch_total` · `gateway_upstream_duration_seconds`
- iam `security-service` (10): `security_account_service_duration_seconds` · `security_dedup_hit_total` · `security_dlq_total` · `security_event_processing_duration_seconds` · `security_events_consumed_total` · `security_login_history_total` · `security_login_history_write_total` · `security_outbox_lag_seconds` · `security_risk_score_distribution` · `security_suspicious_detected_total`
- wms `admin-service` (5): `admin.event.dedupe.hit.count` · `admin.event.dedupe.late.count` · `admin.event.dedupe.table.size` · `admin.idempotency.mismatch.count` · `admin.idempotency.pending.count`
- wms `contracts,inbound-service` (4): `inbound.webhook.inbox.pending.count` · `inbound.webhook.processing.failure.total` · `inbound.webhook.processing.lag.seconds` · `inbound.webhook.received.total`
- wms `contracts,outbound-service` (4): `outbound.webhook.inbox.pending.count` · `outbound.webhook.processing.failure.total` · `outbound.webhook.processing.lag.seconds` · `outbound.webhook.received.total`
- wms `inbound-service` (7): `inbound.consumer.dlt.records.total` · `inbound.consumer.received.total` · `inbound.event.dedupe.table.size` · `inbound.idempotency.mismatch.count` · `inbound.outbox.lag.seconds` · `inbound.outbox.publish.failure.total` · `inbound.webhook.inbox.failed.count`
- wms `inventory-service` (5): `inventory.event.dedupe.table.size` · `inventory.reservation.confirmed.total` · `inventory.reservation.released.total` · `inventory.reservation.reserve.rejected.total` · `inventory.reservation.reserved.total`
- wms `master-service` (5): `master.idempotency.check.count` · `master.idempotency.entry.ttl.remaining.seconds` · `master.idempotency.lock.wait.ms` · `master.idempotency.replay.latency.ms` · `master.idempotency.store.failure.count`
- wms `notification-service` (8): `notification.channel.send.failed` · `notification.consumer.dlt.records.total` · `notification.consumer.received.total` · `notification.outbox.lag.seconds` · `wms.notification.dedupe.constraint.violation` · `wms.notification.dedupe.outcome` · `wms.notification.delivery.retry.queue.depth` · `wms.notification.delivery.terminal`
- wms `outbound-service` (12): `outbound.consumer.dlt.records.total` · `outbound.consumer.received.total` · `outbound.event.dedupe.table.size` · `outbound.outbox.lag.seconds` · `outbound.outbox.publish.failure.total` · `outbound.saga.active.count` · `outbound.saga.compensation.fired.count` · `outbound.saga.completed.duration.seconds` · `outbound.saga.event.already_applied.count` · `outbound.saga.event.invalid_transition.count` · `outbound.saga.state.transitions` · `outbound.webhook.inbox.failed.count`

#### E 스팬 (23)

- iam `account-service` (7): `account.anonymize` · `account.delete.request` · `account.internal.lock` · `account.outbox.write` · `account.profile.update` · `account.signup` · `account.status.change`
- iam `admin-service` (4): `admin.audit.begin` · `admin.audit.complete` · `admin.auth.verify` · `admin.outbox.write`
- iam `auth-service` (6): `auth.credential.verify` · `auth.login` · `auth.outbox.write` · `auth.token.issue` · `auth.token.refresh` · `auth.token.reuse.detect`
- iam `gateway-service` (3): `gateway.filter.jwt` · `gateway.filter.ratelimit` · `gateway.jwks.fetch`
- iam `security-service` (3): `security.dedup.check` · `security.history.write` · `security.outbox.write`

#### F 폐기 서비스 스펙 (7)

- ecommerce `auth-service-deprecated` (7): `auth_login_failure_total` · `auth_login_total` · `auth_logout_total` · `auth_session_eviction_total` · `auth_signup_total` · `auth_token_refresh_total` · `event_publish_failure_total`


## AC-5 — 소유자 결정 선택지와 에이전트 추천

| 범주 | 선택지 | 에이전트 추천 | 모델 |
|---|---|---|---|
| A2 (3) | 스펙 이름을 코드 이름으로 고친다 / 코드를 스펙 이름으로 바꾼다 | **스펙을 고친다** — 코드 이름은 이미 대시보드 · 알림 · 가드(833)가 쓰는 쪽이다 | Sonnet |
| B (6) | 스펙 행을 실제 프레임워크 시리즈 이름 + 라벨로 고친다 / 스펙 이름대로 직접 계측을 추가한다 | **스펙을 고친다** — 같은 값을 두 번 세지 않는다. admin · wms notification 은 그 티켓에서 scrape 로 한 번 확인. iam `auth-overview.json` 의 `auth_circuit_breaker_state` 패널도 같이 | Sonnet |
| C (12) | 스펙에 «파생 — PromQL» 로 식을 적는다 / 계측으로 만든다 | **식을 적는다** — 비율은 카운터 둘로 계산된다(`hit/(hit+miss+…)`). 계측으로 만들면 시간 창이 고정된다 | Sonnet |
| D1 (6) | 구현 / 대시보드 패널 삭제 / 스펙 · 대시보드 둘 다 삭제 | **구현** — 운영자가 보는 화면이 비어 있다. iam 5 는 `TASK-BE-662` · `664` 의 방식(`*MetricsPort` + Micrometer 어댑터) 그대로, ecommerce 1 은 기존 `payment_*_total` 옆에 | Sonnet(iam) · Sonnet(ecommerce) |
| D2 (120) | (a) 전부 구현 (b) 스펙에 «구현 상태» 를 두고 미구현을 «계획» 으로 표시, 알림 · 대시보드가 필요해질 때 구현 (c) 스펙에서 삭제 | **(b)** — 120개를 수요 없이 구현하면 아무도 안 보는 시계열이 생기고, 삭제하면 설계 의도가 사라진다. iam 의 «필수 메트릭» 표 5개(52행)는 32행이 없다(D2 27 + D1 5 — iam D2 36 중 나머지 9는 «비즈니스 메트릭» 표) → «필수» 라는 말이 지금 거짓이므로 표 제목부터 바꿔야 한다(소유자 결정). 셋째 방향 가드(스펙 → 코드)는 (b) 뒤에 «계획» 이 아닌 행만 물게 하면 된다 | 결정 후 Opus(가드) · Sonnet(문서) |
| E (23) | Observation API 로 span 을 만든다 / 스펙 표를 «계획» 으로 / 삭제 | **«계획» 으로** — 저장소 전체에 수동 span 생성 코드가 0이다(HTTP · Kafka 자동 계측 span 만 있다). 23개를 만들려면 트레이싱 백엔드에서 실제로 보는지부터 정해야 한다 | — |
| F (7) | 그대로 / 스펙 디렉터리에 «폐기» 머리말 | **그대로** — ecommerce `apps/auth-service` 가 아직 이 이름들을 등록하고 `business-metrics.json` 이 묻는다. 서비스 폐기 때 같이 | — |
| 곁가지: wms 대시보드 = iam 복사본 | wms 것으로 다시 쓴다 / 지운다 | **wms 것으로 다시 쓴다** — `TASK-MONO-834` 가 알림에서 한 것과 같은 일. 결정 없이 진행 가능한 결함 수정 | Sonnet |

## 재현 — 측정 스크립트

`python -I census.py <repo-root> census.tsv` → `census.tsv`(행) + `census.tsv.reg`(등록 이름). 분류는 `census.tsv` 를 (project, kind, name) 으로 묶은 뒤: 등록됨 → A · 스펙 디렉터리가 `*-deprecated` → F · 이름이 `hit_rate`/`_ratio`/`_rate` 로 끝나거나 설명에 «derivable»/«Computed from» → C · 그 외 손 판정(A2 3 · B 6 · C 1 · D1 6 은 위 표의 근거로) · 나머지 D2. 리터럴만 일치한 11행은 AC-1 2 대로 손 판정.

<details><summary><code>census.py</code></summary>

```python
"""Census: metric/span names a spec declares vs what code registers.

Spec population: every projects/<p>/specs/**/*.md. A table row counts when some enclosing heading
contains metric/메트릭 (kind=metric) or, failing that, span/trac/추적 (kind=span). Name = each
backticked token of the row's FIRST cell that looks like a dotted/underscored lowercase name.

Code, per project (apps/*/src/main + libs/*/src/main, .java/.kt):
  literals   = every "a.b" / "a_b" string literal
  registered = literal or String-constant value passed to Counter/Timer/Gauge/DistributionSummary/
               LongTaskTimer/FunctionCounter/FunctionTimer.builder( or .counter(/.timer(/.gauge(/.summary(
Normalise: '.' and '-' -> '_'. Prometheus suffixes stripped from the SPEC name for matching:
  _total _count _sum _bucket _max, then _seconds.

status:
  registered  - spec name (or suffix-stripped) == a registered meter name   -> implemented
  literal     - matches only a non-meter literal (topic, event, span, key)   -> suspicious, check by hand
  missing     - no literal at all
"""
import re, subprocess, sys, os

ROOT, OUT = sys.argv[1], sys.argv[2]

def ls(*pats):
    r = subprocess.run(['git', '-C', ROOT, 'ls-files', '-z', '--', *pats], capture_output=True)
    return [p for p in r.stdout.decode('utf-8').split('\0') if p]

NAME = re.compile(r'^[a-z][a-z0-9]*([._][a-z0-9]+)+$')
HEAD = re.compile(r'^(#+)\s+(.*)$')
METRIC_HEAD = re.compile(r'metric|메트릭|observability|관측', re.I)
SPAN_HEAD = re.compile(r'\bspans?\b|\btraces?\b|\btracing\b|추적', re.I)
LOG_HEAD = re.compile(r'\blogs?\b|logging|로그|로깅|\bmdc\b', re.I)

rows = []
for f in ls('projects/*/specs/**/*.md'):
    proj = f.split('/')[1]
    parts = f.split('/')
    svc = parts[4] if len(parts) > 4 and parts[3] == 'services' else parts[3]
    text = open(os.path.join(ROOT, f), encoding='utf-8', errors='replace').read().replace('\r', '')
    stack = []
    for line in text.split('\n'):
        m = HEAD.match(line)
        if m:
            lvl = len(m.group(1))
            stack = [s for s in stack if s[0] < lvl] + [(lvl, m.group(2).strip())]
            continue
        if not line.startswith('|'):
            continue
        titles = [t for _, t in stack]
        kind = None
        for t in reversed(titles):          # innermost classifying heading wins
            if LOG_HEAD.search(t) and not METRIC_HEAD.search(t.replace('Observability', '')):
                break                       # log-field tables are not metrics
            if SPAN_HEAD.search(t):
                kind = 'span'; break
            if METRIC_HEAD.search(t):
                kind = 'metric'; break
        if kind is None:
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        for name in re.findall(r'`([^`]+)`', cells[0]):
            name = re.sub(r'\{.*$', '', name.strip())
            if not NAME.match(name):
                continue
            typ = cells[1].strip('` ') if len(cells) > 1 else ''
            rows.append((proj, svc, kind, name, typ, ' > '.join(titles[1:]), f))

LIT = re.compile(r'"([a-z][a-z0-9]*(?:[._-][a-z0-9]+)+)"')
REG = re.compile(r'(?:(?:Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer)\.builder|\.(?:counter|timer|gauge|summary))\(\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_.]*)')
CONST = re.compile(r'final\s+String\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"')

def scan(files):
    lits, reg, consts, refs = set(), set(), {}, set()
    for f in files:
        t = open(os.path.join(ROOT, f), encoding='utf-8', errors='replace').read()
        lits.update(LIT.findall(t))
        for n, v in CONST.findall(t):
            consts.setdefault(n, set()).add(v)
        for a in REG.findall(t):
            if a.startswith('"'):
                reg.add(a.strip('"'))
            else:
                refs.add(a.split('.')[-1])
    for r in refs:
        reg |= consts.get(r, set())
    return lits, reg

def norm(x):
    return x.replace('.', '_').replace('-', '_')

SUFFIXES = ['_total', '_count', '_sum', '_bucket', '_max', '_seconds']
def candidates(n):
    out = [n]
    for s in SUFFIXES:
        for x in list(out):
            if x.endswith(s) and x[: -len(s)] not in out:
                out.append(x[: -len(s)])
    return out

lib_l, lib_r = scan(ls('libs/*/src/main/**/*.java', 'libs/*/src/main/**/*.kt'))
code = {}
for proj in sorted({r[0] for r in rows}):
    l, r = scan(ls(f'projects/{proj}/apps/*/src/main/**/*.java', f'projects/{proj}/apps/*/src/main/**/*.kt'))
    code[proj] = ({norm(x): x for x in l | lib_l}, {norm(x): x for x in r | lib_r})

with open(OUT, 'w', encoding='utf-8', newline='\n') as o:
    o.write('project\tservice\tkind\tname\ttype\tstatus\tmatch\tsection\tfile\n')
    for proj, svc, kind, name, typ, sect, f in rows:
        lits, reg = code[proj]
        status, match = 'missing', ''
        for c in candidates(norm(name)):
            if c in reg:
                status, match = 'registered', reg[c]
                break
        if status == 'missing':
            for c in candidates(norm(name)):
                if c in lits:
                    status, match = 'literal', lits[c]
                    break
        o.write('\t'.join([proj, svc, kind, name, typ, status, match, sect, f]) + '\n')
with open(OUT + '.reg', 'w', encoding='utf-8', newline='\n') as o:
    for proj, (lits, reg) in code.items():
        for k in sorted(reg):
            o.write(proj + '\t' + k + '\n')
print('rows', len(rows))
```

</details>

# Definition of Done

- AC 전부 · 머지 · 4-dim close · 소유자에게 결정 선택지 전달.
