# Task ID

TASK-MONO-836

# Title

p99 지연 알림이 묻는 `http_server_requests_seconds_bucket` 을 아무 서비스도 내보내지 않는다 — `libs/java-observability` 가 `http.server.requests` 히스토그램을 켜고, 그 라이브러리를 쓰지 않는 wms 두 서비스는 설정으로 켠다

# Status

done

# Owner

monorepo

# Task Tags

- lib
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (MeterFilter 하나 + 설정 2줄 + 테스트)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-835` 증거 «남은 것» → 소유자 «진행».

# Dependency Markers

- 선행: `TASK-MONO-834` · `TASK-BE-664`(done) — 알림이 묻는 이름은 이제 다 존재하는데, 이 하나만 아무도 내보내지 않는다.

# Goal

2026-11-25 UTC 실측:

- `HighLatencyP99`(iam `alert-rules.yml:32` · wms `:46`)와 ecommerce 의 p99 알림 셋(`:53` · `:66` · `:79`)은 모두 `histogram_quantile(0.99, … rate(http_server_requests_seconds_bucket[5m]) …)` 을 쓴다.
- Spring Boot 는 `http.server.requests` 타이머의 **버킷을 기본으로 내보내지 않는다**(`_count` · `_sum` · `_max` 만). 버킷은 `management.metrics.distribution.percentiles-histogram.http.server.requests=true` 또는 같은 효과의 `MeterFilter` 가 있어야 생긴다.
- `libs/**/src/main` · `projects/**/src/main` 에 `percentiles-histogram` · `percentilesHistogram` · `publishPercentileHistogram` · `DistributionStatisticConfig` · `serviceLevelObjectives` 가 **0건**.
- → 다섯 알림은 언제나 no-data 이고 **절대 울리지 않는다**. `TASK-MONO-834` 의 가드는 `http_server_requests_` 를 프레임워크 접두사로 통과시키므로(이름이 *존재하는지* 는 런타임만 안다) 이것을 못 본다.
- `platform/api-gateway-policy.md:185` 는 게이트웨이에 «latency histogram — per route and per downstream service» 를 요구한다.

# Scope

## In Scope

1. **`libs/java-observability`** `ObservabilityAutoConfig` 의 `MicrometerCommonTagsConfig`(또는 옆 설정 클래스)에 `MeterFilter` 빈 하나: 이름이 정확히 `http.server.requests` 인 미터에만 `percentilesHistogram(true)`. 다른 미터는 건드리지 않는다(카디널리티).
2. **명시적 끄기 존중**: 서비스가 `management.metrics.distribution.percentiles-histogram.http.server.requests=false` 를 적으면 꺼져야 한다(필터가 그 속성을 덮어쓰지 않는다).
3. **라이브러리를 쓰지 않는 서비스**: `projects/wms-platform/apps/{admin-service,notification-service}` 는 `libs:java-observability` 를 의존하지 않는다(그런데 wms `prometheus.yml` 이 둘을 긁는다). 각 `application.yml` 에 위 속성 `true` 를 적는다. 그 라이브러리를 의존하게 바꾸지는 않는다(MDC 필터 · 공통 태그 같은 다른 동작이 따라 들어온다).
4. **테스트**(`libs/java-observability/src/test`): ① `http.server.requests` 타이머를 기록하면 Prometheus scrape 에 `http_server_requests_seconds_bucket` 이 있다 ② **대조군** — 필터 없이는 같은 기록에서 `_bucket` 이 없다(이 테스트가 «원래 없었다» 는 이 티켓의 전제를 증명한다) ③ 다른 이름의 타이머에는 `_bucket` 이 생기지 않는다 ④ 속성 `false` 면 `_bucket` 이 없다(Spring Boot 의 속성 필터와 함께 띄운 컨텍스트에서 — 필터 순서가 이것을 정한다).
5. 두 프로젝트의 `knowledge/runbooks/high-latency-p99.md` 에 있는 «설정이 안 보인다 — no-data 가능» 문단을 고친 뒤 상태로 바꾼다.

## Out of Scope

- 알림 식 · 임계값 변경.
- SLO 버킷(`serviceLevelObjectives`) · 최소/최대 기대값 조정 — 기본 버킷을 쓴다.
- `http.client.requests` 등 다른 미터.
- 데모 인스턴스 재굽기.

# Acceptance Criteria

- [x] AC-1: 테스트 ①~④ 가 통과한다. ② 는 필터를 끈 상태에서 `_bucket` 이 **없음** 을 단언한다.
- [x] AC-2: 실제 서비스 하나(iam `auth-service` 권장 — `TASK-BE-664` 가 노출을 열었다)의 기존 Spring 컨텍스트 테스트 또는 새 테스트에서 `/actuator/prometheus` 응답에 `http_server_requests_seconds_bucket` 이 나온다. 띄우지 못하면 ⚪ 와 그 이유.
- [x] AC-3: wms `admin-service` · `notification-service` 의 `application.yml` 에 속성이 있고 두 서비스 테스트가 통과한다.
- [x] AC-4: `:libs:java-observability:check` 와 영향받는 서비스 모듈의 테스트가 통과한다(라이브러리를 쓰는 41개 모듈 전부는 CI 에 맡기되, 로컬에서 최소 iam 5개).
- [x] AC-5: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께). PR CI 실패 0.

# Related Specs

- `platform/api-gateway-policy.md` § Observability(:185)
- `platform/shared-library-policy.md` — `libs/` 는 프로젝트에 무관해야 한다(이 필터는 기술 메커니즘이라 해당)
- 각 프로젝트 `infra/prometheus/alert-rules.yml`

# Related Contracts

- 없음.

# Edge Cases

- WebFlux 게이트웨이(iam · ecommerce · wms 등)도 같은 미터 이름 `http.server.requests` 를 쓴다 — 필터는 서블릿/리액티브와 무관하게 이름으로 고른다.
- 라이브러리를 쓰는 다른 다섯 프로젝트(erp · fan · finance · scm · ecommerce)에서도 시계열이 늘어난다(경로 × 메서드 × 상태 × 버킷 수). 로컬 · 데모 규모에서 허용. 끄고 싶은 서비스는 AC 의 속성으로 끈다.

# Failure Scenarios

- 필터가 Spring Boot 의 속성 필터보다 나중에 적용되어 명시적 `false` 를 덮어쓴다 → 테스트 ④ 가 잡는다.
- 필터가 접두사로 고르다가 `http.server.requests.active`(LongTaskTimer) 까지 히스토그램으로 만든다 → 정확 일치로 고른다. 테스트 ③ 이 다른 이름을 본다.
- 테스트가 `SimpleMeterRegistry` 로만 재서 Prometheus 이름 변환을 안 본다 → ①② 는 `PrometheusMeterRegistry` scrape 문자열로 단언한다.

# Test Requirements

- 라이브러리 단위 테스트 ①~④ · 서비스 하나의 scrape 테스트 · 영향 모듈 테스트 · 가드 전수 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: `ObservabilityAutoConfig.HttpServerRequestsHistogramConfig` — `MeterFilter` 하나. 이름이 **정확히** `http.server.requests` 이고 `config.isPercentileHistogram()` 가 `null`(아무도 정하지 않음)일 때만 `percentilesHistogram(true)` 를 merge. 명시적 값이 있으면 그대로 둔다 → Spring Boot 의 속성 필터와 어느 순서로 돌아도 명시적 `false` 가 이긴다. wms `admin-service` · `notification-service` `application.yml` 에 `management.metrics.distribution.percentiles-histogram.http.server.requests: true`. 두 프로젝트 `high-latency-p99.md` 를 고친 뒤 상태로.
- **AC-1** — `HttpServerRequestsHistogramTest` 5건 PASS(Spring Boot 의 `MetricsAutoConfiguration` + `PrometheusMetricsExportAutoConfiguration` 으로 띄운 실제 `PrometheusMeterRegistry` scrape): ① `_bucket{` 있음 ② **대조군 — 라이브러리 없이 같은 기록에서 `_count` 는 있고 `_bucket` 은 없음**(티켓 전제의 증명) ③ `http.server.requests.other` · `outbox.publish` 에는 `_bucket` 없음 ④ `…http.server.requests=false` 존중 ④' `…all=false` 존중. 기존 `ObservabilityAutoConfigTest` 의 `hasSingleBean(MeterFilter)` 는 필터가 둘이 되어 빈 이름 단언으로 바꿈. **bite**: 필터 이름을 다른 값으로 바꾸면 10건 중 1건(①) 실패 → 복원.
- **AC-2** — iam auth-service 대신 **wms master-service** 의 기존 실서버 IT `WarehouseIntegrationTest.prometheusEndpoint_exposesOutboxMetrics`(`@AutoConfigureObservability`, 라이브러리 경로)에 `http_server_requests_seconds_bucket{` 단언 추가 — 5/5 PASS. auth-service 에는 `/actuator/prometheus` 를 실제로 긁는 테스트가 없고(노출 테스트는 yml 읽기), 같은 라이브러리 경로를 실서버로 증명하는 기존 IT 가 있어 그것을 썼다.
- **AC-3** — notification-service `HealthSurfaceIntegrationTest` 에 버킷 case 추가(실서버, yml 경로) 6/6 PASS. **bite**: yml 값을 `false` 로 바꾸면 6건 중 1건 실패 → 복원(점이 든 YAML 키가 실제로 바인딩된다는 증명). admin-service 는 스크레이프 IT 가 없어 `HttpServerRequestsHistogramConfigTest`(실제 `application.yml` 읽기) 1/1 PASS.
- **AC-4** — 로컬 `:libs:java-observability:check` + wms admin · notification · master · gateway `test` + iam 5개 `test`: 하나만 실패 — security-service `AccountLockHistoryJpaRepository > initializationError`(Testcontainers MySQL 컨테이너 기동 실패 `ContainerLaunchException`, 단언 전) → 그 클래스만 재실행 rc=0. 나머지 라이브러리 소비 모듈은 CI.
- **AC-5** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(`check-erp-single-tenant-ratchet` — 실행 중 erp DB · `check-prerendered-demo-verdict` — `DEMO_API_BASE` + 빌드, CI frontend-checks 에서 돈다). `.mjs` 6/6. `scripts/` 추가·삭제 없음. PR CI(#4682): 실패 0 · SUCCESS 35 · SKIPPED 40 — wms `Integration (master-service + notification-service + outbound-service …)` 가 **실행되어** SUCCESS.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
