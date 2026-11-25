# Task ID

TASK-BE-669

# Title

payment-service 스펙의 `payment_failed_total`(by reason) 이 구현되지 않아 비즈니스 대시보드 패널이 비어 있다(837 D1) — 구현하고 대기 목록에서 지운다

# Status

review

# Owner

backend

# Task Tags

- observability
- payment-service

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (기존 `payment_*_total` 옆에 카운터 하나 — 실패 분기 고르기)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` AC-5 «D1 = 구현» → 소유자 «권장안대로 진행».

# Dependency Markers

- 선행: `TASK-MONO-839`(대기 목록 `projects/ecommerce-microservices-platform/infra/grafana/pending-metrics.txt` — 구현되면 그 항목이 남아 있는 한 가드가 실패한다).

# Goal

`specs/services/payment-service/observability.md:15` — `payment_failed_total` | Counter | «Total payment failures by reason». `infra/grafana/dashboards/business-metrics.json` 이 `rate(payment_failed_total{job="payment-service"}[5m])` 를 묻는다. 코드에는 `payment_created_total` · `payment_completed_total` · `payment_refunded_total` 등은 있으나 이것이 없다.

# Scope

## In Scope

1. `payment_failed_total{reason}` 을 기존 payment metric 과 같은 자리(같은 어댑터 · 같은 등록 방식)에 등록하고, 결제가 **실패로 확정되는** 지점에서 센다. `reason` 값 집합은 코드의 실패 분기에서 정하고 증거에 파일:줄로(PG 거절 · 검증 실패 등). 인프라 장애(PG 타임아웃 뒤 재시도 중 등 아직 실패로 확정 안 된 상태)는 세지 않는다.
2. 스펙 행에 `reason` 값 집합을 적는다(스펙이 «by reason» 만 적고 값이 없다).
3. `projects/ecommerce-microservices-platform/infra/grafana/pending-metrics.txt` 에서 그 줄 삭제 — 남는 항목이 없으면 파일 삭제.
4. 테스트: metric 단위 테스트 + 실패 확정 지점 테스트.

## Out of Scope

- 다른 ecommerce 미구현 metric(837 D2).

# Acceptance Criteria

- [x] AC-1: 실제 `PrometheusMeterRegistry` scrape 에 `payment_failed_total{reason=…}` 로 나온다(테스트).
- [x] AC-2: 실패 확정 지점이 증거에 파일:줄로, 각 `reason` 에 테스트.
- [x] AC-3: `check-alert-rule-metrics.sh` rc=0 · `check-metric-name-registry.sh` rc=0.
- [ ] AC-4: payment-service `test` 통과 · 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/payment-service/observability.md`

# Related Contracts

- 없음.

# Edge Cases

- 같은 결제가 재시도 끝에 실패 확정되면 한 번만 센다.
- 환불 실패는 결제 실패가 아니다(`payment_refund_stranded_*` 가 따로 있다).

# Failure Scenarios

- 재시도마다 세어 같은 실패가 여러 번 → 실패율 과대.

# Test Requirements

- 위 테스트 · 실제 scrape · service test · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: `PaymentMetrics`(`adapter/out/metrics`) 에 `payment_failed_total{reason}` 카운터 추가(기존 `payment_*_total` 과 같은 어댑터, `Counter.builder(...).tag("reason", …).register(registry)`). 포트 `PaymentMetricRecorder` 에 `incrementPaymentFailed(String reason)` + 상수 `FAILED_REASON_PG_REJECTED="pg_rejected"`. 스펙 `observability.md` 의 해당 행 설명 셀에 reason 값 집합 기재. `payment-service/build.gradle` 에 `testImplementation 'io.micrometer:micrometer-registry-prometheus'`(libs 쪽은 `implementation` 이라 test classpath 에 없었음). `pending-metrics.txt` 는 남는 항목이 없어 `git rm`.
- **실패 확정 지점(전수)**: payment-service main 에서 `Payment.fail()` 호출은 단 한 곳 — `application/service/PaymentConfirmService.java:68`(`PgConfirmFailedException` catch, PG 4xx 확정 거부 → 행을 FAILED 로 잠금). 카운트는 save 뒤 `:70`. reason 값은 `pg_rejected` 하나뿐.
- **세지 않는 분기(의도)**: `PgGatewayUnavailableException`(`:71`, 재시도 가능) · value-decline `approved=false`(`:115`, PENDING 유지 후 503 — 코드 주석이 FAILED 로 잠그지 않는다고 명시) · post-capture 환불 실패(stranded, 별도 `payment_refund_stranded_*`). 도메인 `Payment.java:122` 는 `fail()` 내부 전이이며 호출자는 위 한 곳.
- **테스트**: `PaymentMetricsTest` — reason 별 증가 + 실제 `PrometheusMeterRegistry.scrape()` 검증. `PaymentConfirmServiceTest` — PG 4xx 거부 시 `incrementPaymentFailed("pg_rejected")` 1회, `PgGatewayUnavailableException` 시 `never()`.
- **관측한 scrape 줄**: `payment_failed_total{reason="pg_rejected"} 1.0`
- **payment-service `test`**: tests=211 skipped=0 failures=0 errors=0, gradle rc=0.
- **가드(스테이지 뒤)**: `check-alert-rule-metrics.sh` rc=0 (`55 metric reference(s) in 3 rules file(s) … 239 reference(s) in 20 dashboard(s), all exported except 11 listed as pending`) · `check-metric-name-registry.sh` rc=0 (`113 literal metric name(s) across 31 service(s), each documented`).
- AC-4(PR CI)는 CI 몫으로 남김.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
