# Task ID

TASK-BE-666

# Title

wms Grafana 대시보드 9개가 iam 것의 복사본이다 — wms 에 없는 서비스(auth · account · security) 대시보드 셋을 지우고, admin · gateway 를 wms 지표로 다시 쓰고, wms 업무 지표 대시보드를 새로 만든다

# Status

review

# Owner

backend

# Task Tags

- observability
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (대시보드 JSON — 지표 이름 · 라벨은 코드에서 확인한 것만)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` 증거 «곁가지 결함» → 소유자 «진행».

# Dependency Markers

- 선행: `TASK-MONO-834`(wms 알림 규칙이 iam 복사본이던 것을 wms 것으로 — 같은 결함의 알림 쪽) · `TASK-MONO-836`(`http_server_requests_seconds_bucket` 이 이제 나온다 — p99 패널이 처음으로 값을 갖는다).

# Goal

2026-11-25 UTC 실측(`projects/wms-platform/infra/grafana/dashboards/`, compose 가 `wms-grafana` 에 마운트):

- 9개 중 8개가 `projects/iam-platform/infra/grafana/dashboards/` 의 같은 이름 파일과 **바이트 단위로 같다**(`cmp`). `auth-overview.json` 도 diff ±4줄. 마지막 변경 `a45707894`(2026-04-18 저장소 재구성).
- `account-overview` · `auth-overview` · `security-overview` 는 wms 에 **없는 서비스** 의 iam 지표(`auth_login_total` · `account_signup_total` · `security_dlq_depth` …)를 묻는다 → 전 패널 빈 화면.
- `admin-overview` 는 wms 에도 admin-service 가 있지만 iam admin 의 지표(`admin_command_total` · `admin_audit_*`)를 묻는다. wms admin-service 가 내는 것은 `admin.projection.*` · `admin.query.latency.p95` · `admin.outbox.*` 다.
- `gateway-overview` 는 HTTP 패널(프레임워크)은 맞고, `gateway_jwt_validation_total` · `gateway_ratelimit_rejected_total` · `gateway_jwks_cache_*` 는 wms gateway 가 내지 않는다(wms 등록: `gateway_jwt_audience_total` · `gateway_ratelimit_redis_unavailable_total` · `gateway_ratelimit_unexpected_error_total`, `libs/java-gateway` · `libs/java-security`).
- `jvm` · `service-overview` · `system-overview` · `alerts` 는 프레임워크 지표만 묻는다 → 내용은 wms 에서도 맞다. 단 `alerts.json` 의 패널 11개가 datasource uid `"prometheus"` 를 쓰는데 wms 가 프로비저닝하는 uid 는 `PBFA97CFB590B2093` 다(나머지 63개 참조는 이 값). iam · ecommerce 의 `alerts.json` 도 같다(이 티켓 범위 밖 — 후속).
- wms 업무 지표(outbox · 재고 예약 · 출고 saga sweeper · 알림 전달 · 멱등성)를 보여 주는 대시보드는 **하나도 없다**.

# Scope

## In Scope

1. **삭제**: `account-overview.json` · `auth-overview.json` · `security-overview.json`.
2. **다시 쓰기** `admin-overview.json` — wms admin-service: projection lag(`admin_projection_lag_seconds_*`, 라벨 `source_service` · `topic`) · dropped(`reason`) · error(`topic`) · query latency(`endpoint`) · outbox pending/success/failure · HTTP 4xx/5xx/p99.
3. **다시 쓰기** `gateway-overview.json` — HTTP 패널 유지 + wms gateway 지표(`gateway_jwt_audience_total{outcome}` · rate-limit fail-open 두 카운터).
4. **새로** `wms-operations.json` — 서비스별 outbox pending · publish success/failure · `inventory.reservation.active.count` · `inventory.mutation.count{operation}` · optimistic-lock retry · reservation expiry swept · outbound saga sweeper(run · recovery fired · exhausted) · notification delivery attempts(`status`) · delivery duration · routing classified/dedupe(`outcome`) · 멱등성 lookup(`result`) · store failure · mismatch.
5. `alerts.json` 의 datasource uid 11곳을 `PBFA97CFB590B2093` 로.
6. 모든 PromQL 의 지표 이름은 **실제 Prometheus 이름**(Micrometer 이름 변환 뒤)으로 쓴다 — 실제 `PrometheusMeterRegistry` 로 각 meter 를 등록해 scrape 한 이름으로 확정(일회용 측정, 커밋 안 함).

## Out of Scope

- iam · ecommerce 대시보드(iam 대시보드에도 스펙 · 코드 어디에도 없는 이름이 있다 — `account_profile_reads_total` · `security_rules_triggered_total` 등. 별 티켓).
- 대시보드 지표 존재 가드 — iam 이 위 이유로 첫날 빨강이 되므로 iam 정리와 함께.
- 새 지표 구현. 코드 변경 없음.

# Acceptance Criteria

- [x] AC-1: wms 대시보드의 모든 `expr` 에서 뽑은 지표 이름이 (a) wms 코드 · `libs` 가 등록한 meter 의 실제 Prometheus 이름 또는 (b) 프레임워크 시리즈(`http_server_requests_*` · `jvm_*` · `process_*` · `system_*` · `hikaricp_*` · `kafka_consumer_*` · `up` · `ALERTS`) 중 하나다 — 이름 목록과 대조 결과를 증거에.
- [x] AC-2: 모든 패널의 datasource uid 가 wms 프로비저닝 uid 와 같다.
- [x] AC-3: `grafana/grafana:11.2.0` 컨테이너에 wms `provisioning` · `dashboards` 를 마운트해 띄우면 대시보드 7개가 오류 없이 프로비저닝된다(`/api/search` 목록 + 로그에 provisioning 오류 0).
- [ ] AC-4: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/*/observability` 관련 절(`idempotency.md` · `external-integrations.md` · `sagas/*.md`)
- `TEMPLATE.md` § Local Network Convention(`grafana.wms.local`)

# Related Contracts

- 없음.

# Edge Cases

- 대시보드 uid 가 iam 과 같다(`admin-overview` 등) — 프로젝트별 Grafana 인스턴스라 충돌은 없지만, 새 파일은 `wms-` 접두사 uid 를 쓴다.
- `admin.query.cache.hit.rate` 는 `meterRegistry.gauge(name, 0.0d)` 로 등록된다 — 박싱된 상수라 값이 갱신되지 않는다(코드 결함, 이 티켓 밖). 대시보드에 넣지 않고 증거에 기록.
- Gauge 이름이 `.count` 로 끝나면 Prometheus 이름은 `_count` 로 끝난다(`*_outbox_pending_count`, `TASK-MONO-834` 알림이 실측) — Counter `x.count` 는 `x_count_total`.

# Failure Scenarios

- Micrometer 이름 변환을 손으로 추측해 `_seconds` 가 두 번 붙거나 `_total` 이 빠진다 → 패널이 다시 빈다. AC-1 이 실제 scrape 이름을 요구한다.
- JSON 은 맞지만 Grafana 가 스키마를 거부한다 → AC-3.

# Test Requirements

- 실제 scrape 이름 대조 · Grafana 프로비저닝 기동 · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: 삭제 3(`account-overview` · `auth-overview` · `security-overview`) · 다시 쓰기 2(`admin-overview` — uid 유지, 제목 «wms admin-service Overview», 패널 11 · `gateway-overview` — 패널 6) · 신설 1(`wms-operations`, 패널 13) · `alerts.json` datasource uid 11곳 `"prometheus"` → `"PBFA97CFB590B2093"`. `jvm` · `service-overview` · `system-overview` 는 프레임워크 지표만 묻고 `$job` 변수(`label_values(…, job)`)를 써 그대로 둠. 코드 변경 없음.
- **이름 확정**: 실제 `PrometheusMeterRegistry` 에 wms 가 쓰는 모양(Gauge · Counter · Timer, 이름 그대로)을 등록해 scrape — 일회용 테스트, 커밋 안 함(실행 뒤 삭제). 확인된 변환: Counter `x.count` → `x_count_total`(`admin_projection_error_count_total` · `inventory_mutation_count_total` · `outbound_saga_sweeper_run_count_total`) · Gauge `x.count` → `x_count`(`*_outbox_pending_count` · `inventory_reservation_active_count`) · Timer `admin.query.latency.p95` → `admin_query_latency_p95_seconds_{count,sum,max}` · Timer `admin.projection.lag.seconds` → `admin_projection_lag_seconds_{count,sum,max}`(`_seconds` 가 두 번 붙지 않음) · Counter `inventory.optimistic-lock.retry.count` → `inventory_optimistic_lock_retry_count_total`. 이 Timer 들은 히스토그램이 없어 `_bucket` 이 없다 → 평균(`_sum/_count`)과 `_max` 로 그렸다.
- **AC-1** — wms 대시보드 7개의 `expr` 83개에서 지표 이름 56개 추출(문자열 · `{…}` · `[…]` · `by (…)` · 함수 제거) → **알 수 없는 이름 0**. 기준 = 위 scrape 이름 + 같은 모양으로 wms 코드에 등록된 형제 9개(`notification/inbound/inventory/master/outbound_outbox_pending_count` · `notification_outbox_publish_failure_total` · `outbound_idempotency_lookup_count_total` · `outbound_idempotency_store_failure_total` · `gateway_ratelimit_unexpected_error_total`) + 프레임워크 접두사(`http_server_requests_` · `jvm_` · `process_` · `system_` · `hikaricp_` · `kafka_consumer_`) · `up` · `ALERTS`. **bite**: 같은 검사를 `origin/main` 의 옛 wms 대시보드 9개에 돌리면 알 수 없는 이름 **36**(account 6 · admin 8 · auth 7 · gateway 5 · security 10). gateway 지표는 wms gateway 가 `libs:java-gateway`(`FailOpenRateLimiter`) · `libs:java-security`(`AllowedAudiencesValidator`, 태그 `gateway` · `outcome`)를 쓰는 것을 `build.gradle:59,62` 로 확인.
- **AC-2** — 7개 파일의 datasource uid 참조가 전부 `PBFA97CFB590B2093`(`"prometheus"` 0). 실측: 임시 Grafana 에서 `GET /api/datasources/uid/prometheus` → **404 «Data source not found»**, 같은 `POST /api/ds/query` 도 404 — 옛 `alerts.json` 패널 11개는 **실제로 깨져 있었다**. 새 uid 는 200(질의가 datasource 까지 감 — Prometheus 가 없어 `no such host`, 예상대로).
- **AC-3** — `grafana/grafana:11.2.0` 에 wms `provisioning` · `dashboards` 를 읽기 전용 마운트: `/api/search` = 대시보드 **7개**(alert-overview · jvm-dashboard · service-overview · system-overview · admin-overview · gateway-overview · wms-operations), datasource 3개. 로그의 error/warn 4줄은 없는 선택 디렉터리(`provisioning/plugins` · `provisioning/alerting`)와 DB 마이그레이션 안내 — 대시보드 · datasource 프로비저닝 오류 0. 컨테이너 · 이미지 삭제.
- **AC-4** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `.mjs` 6/6. 지운 세 파일을 가리키는 참조 0(`projects/wms-platform`, tasks 제외). PR CI: 대기.
- 🆕 **기록만 (이 티켓 밖)**: ① `admin.query.cache.hit.rate` 는 `ProjectionMetrics.java:61` `meterRegistry.gauge(CACHE_HIT_RATE_METRIC, 0.0d)` — 박싱된 상수를 물어 값이 갱신될 수 없다 → 대시보드에 넣지 않음. ② iam · ecommerce 의 `alerts.json` 도 uid `"prometheus"` 11곳 — 위 404 로 같은 결함. ③ iam 대시보드는 스펙 · 코드 어디에도 없는 이름을 묻는다(예: `account_profile_reads_total` · `security_rules_triggered_total` · `gateway_jwks_cache_hits_total`) — `TASK-MONO-837` 은 스펙이 선언한 이름만 셌으므로 이것을 못 봤다.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
