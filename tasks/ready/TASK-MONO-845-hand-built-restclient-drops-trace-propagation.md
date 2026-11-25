# Task ID

TASK-MONO-845

# Title

손으로 만든 `RestClient` 는 관측되지 않아 client span 도 `traceparent` 도 없다 — 공통 팩토리 `ResilienceClientFactory.buildRestClient` 가 `ObservationRegistry` 를 받게 하고, `platform/observability.md` § Tracing 문장을 실제 계측에 맞춘다

# Status

ready

# Owner

monorepo

# Task Tags

- observability
- lib
- docs

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (공유 라이브러리 시그니처 변경 + 여러 프로젝트 호출 지점을 한 PR 에 — CLAUDE.md § Cross-Project Changes)
> 기안 2026-11-25 UTC. 출처: 세션 인계(2026-11-25)의 «§ Tracing 문장 vs 자동 계측 — 측정 안 함» 후보를 실측 → 소유자 결정 «끊긴 전파만 고치고 문장도 맞추기»(선택지 2, 2026-11-25 UTC).

# Dependency Markers

- 선행: 없음. 관련(같은 규약 계열): `TASK-MONO-842`(observability.md § Spec ↔ Code ↔ Consumers).
- 후속 없음. 범위 밖으로 남긴 것은 § Out of Scope 에 근거와 함께.

# Goal

## 실측 (2026-11-25 UTC)

`platform/observability.md` § Tracing 은 네 가지를 요구한다 — 모든 HTTP 요청의 trace 헤더 전파 · 로그 MDC 의 trace ID · HTTP handler / DB / Redis / 외부 호출 span · OpenTelemetry → Jaeger(또는 호환). 서비스 43개가 세 방식으로 갈린다:

| 묶음 | 방식 | 근거 |
|---|---|---|
| ecommerce 13 | OTel Java agent 2.12.0 — 각 `Dockerfile` 의 `-javaagent` (컨테이너에서만) | `projects/ecommerce-microservices-platform/apps/*/Dockerfile` |
| erp · fan · finance · scm · wms 25 | Micrometer Tracing(`micrometer-tracing-bridge-otel`) + `opentelemetry-exporter-otlp`, `sampling.probability: 1.0` | 각 `build.gradle` · `application.yml` |
| iam 5 | tracer 없음 — runtimeClasspath 에 `opentelemetry-api` 만(SDK · micrometer-tracing 0) | `gradlew :projects:iam-platform:apps:admin-service:dependencies --configuration runtimeClasspath` |

25개 서비스에서 실제로 나오는 것:

- **실측**: federation e2e 2026-11-25 run `45536554024` — 한 trace 에 154 span, 서비스 6(console-web 94 · wms admin-service 19 · erp masterdata 13 · finance account 11 · scm gateway 11 · finance ledger 6). HTTP 서버 span 과 console-web → producer 전파는 동작한다.
- Boot 3.4.1 `spring-boot-actuator-autoconfigure` 의 관측 자동설정은 `WebMvc` · `WebFlux` · `HttpClientObservations` · `Batch` · `GraphQl` 뿐 — **JDBC · Lettuce 관측 자동설정 없음**, `datasource-micrometer` 같은 라이브러리도 없음. Kafka `observation-enabled` 는 25개 서비스에서 0곳.
- **main 코드에 `ObservationRegistry` 참조 0곳.** `RestClient.builder()` / `RestClient.create()` 의 기본 레지스트리는 `ObservationRegistry.NOOP` 이므로, Boot 가 주입하는 `RestClient.Builder` 가 아닌 **손으로 만든 클라이언트는 client span 도 없고 `traceparent` 도 싣지 않는다**(Spring 소스 근거 — 🔴 런타임으로는 재지 않음; AC-2 의 테스트가 그 측정이다).
- 그런 클라이언트의 대표가 공통 팩토리 `libs/java-common/.../ResilienceClientFactory.buildRestClient`(정적, 레지스트리 인자 없음) — main 호출 파일 34개(ecommerce · iam 포함). 25개 서비스 안에서는 내부 호출 `demand-planning → procurement` · `demand-planning → inventory-visibility` 와 외부 호출(ledger fx-rate 2 · fan notification 2 · erp Slack 1 · scm supplier 1)이 여기를 지난다.
- 팩토리 밖에서 `RestClient.builder()` 를 직접 쓰는 25개 서비스 파일: erp `approval-service` `RestClientConfig` · erp `notification-service` `MasterDataCallerEmployeeAdapter` · fan `artist-service` `StoreSellerDirectoryConfig` · fan `community-service` `ArtistAccountCheckerConfig` · `MembershipCheckerAutoConfig` · scm `logistics-service` `VendorHttpClientFactory`.

## 목표

1. 25개 서비스의 **손으로 만든 HTTP 클라이언트가 전부 관측되어** client span 을 만들고 `traceparent` 를 싣는다.
2. `platform/observability.md` § Tracing 의 문장이 **실제로 지켜지는 것만** 요구하게 된다(DB · Redis · Kafka span 은 «요구하지 않음 · 자동 계측 없음» 으로).

# Scope

## In Scope

1. **AC-0 실측 먼저** (구현 전):
   - (a) 위 표 · 호출 지점 목록을 `origin/main` 에서 다시 센다(늘거나 줄었으면 그 수로).
   - (b) Spring Cloud 2024.0.0 gateway 의 전달 경로가 `traceparent` 를 싣는지 — **그 jar 에서** 확인한다(`ObservedRequestHttpHeadersFilter` 등 클래스 존재 + 켜지는 조건 · 속성 이름 인용). 싣지 않으면 그 수리도 이 티켓 범위다; 싣는다면 Implementation Evidence 에 근거만 적는다.
   - (c) 공통 팩토리가 `ObservationRegistry` 를 받을 때 생기는 **부수 효과**: 같은 레지스트리에 Boot 의 meter 핸들러가 붙어 있으면 `http.client.requests` metric 이 새로 생긴다. 그 이름이 이 저장소 가드(`check-metric-name-registry.sh` · `check-alert-rule-metrics.sh`)에 걸리는지, uri 태그 카디널리티 상한(Boot `management.metrics.web.client.max-uri-tags`, 기본 100)이 적용되는지 확인해 적는다.
2. `ResilienceClientFactory.buildRestClient` 에 **필수** `ObservationRegistry` 인자 — 레지스트리 없는 옛 오버로드는 **지운다**(남기면 새 호출자가 조용히 전파를 잃는다; 컴파일러가 34개 호출자를 전부 찾아 준다). 팩토리는 `RestClient.builder()....observationRegistry(registry)` 로 만든다. Boot 의 `RestClient.Builder` 를 받는 방식은 쓰지 않는다 — 메시지 컨버터 · `ObjectMapper` 가 Boot 설정으로 바뀌어 직렬화 동작이 달라질 수 있다(이 티켓은 전파만 바꾼다).
3. 34개 호출 파일 전부(ecommerce · iam 포함)가 주입받은 `ObservationRegistry` 빈을 넘긴다. 테스트에서 `new` 로 만드는 곳은 `ObservationRegistry.NOOP`. ecommerce(agent) · iam(tracer 없음)에서는 tracing 결과가 달라지지 않는다 — 달라지는 것은 AC-0 (c) 의 client metric 뿐이며 그것을 Evidence 에 적는다.
4. 위 «팩토리 밖» 6개 파일에 `.observationRegistry(registry)`.
5. **재발 가드**: tracer 를 내보내는 서비스(`build.gradle` 이 `micrometer-tracing-bridge-otel` 을 선언)의 `src/main/java` 에서 `RestClient.builder(` / `RestClient.create(` / `WebClient.builder(` 를 부르는 파일이 같은 빌더 체인에 `observationRegistry(` 를 갖지 않으면 실패. 술어 · 모집단은 구현자가 정하되 **bite 를 증명**한다(되돌린 줄이 실제로 들어갔는지 `grep` 으로 확인한 뒤 빨강). `scripts/` 에 파일을 더하면 전체 가드를 돌리고 `check-ls-files-guard-count.sh` 가 지목하는 산문을 고친다(CLAUDE.md § Task Rules).
6. `platform/observability.md` § Tracing 문장 개정 — 프로젝트 이름 없이(HARDSTOP-03 정신, platform 은 공용):
   - 전파: «trace 를 내보내는 서비스의 모든 HTTP 요청은 `traceparent` · `tracestate` 를 싣는다 — 나가는 클라이언트는 `ObservationRegistry` 로 관측되어야 한다(Boot 주입 빌더 또는 레지스트리를 넘긴 공통 팩토리)».
   - MDC: «trace 문맥이 있을 때 trace ID 를 MDC `traceId` 에».
   - span: 요구 = HTTP 서버 · HTTP 클라이언트. DB · Redis · 메시징 span 은 «요구하지 않음 — 자동 계측이 없다; Java agent 를 쓰는 서비스는 낸다» 로 명시.
   - 가드 이름을 § Spec ↔ Code ↔ Consumers 처럼 문장에 연결.

## Out of Scope (근거)

- **iam 에 tracer 추가 · DB / Redis / Kafka span 구현** — 소유자가 선택지 3(전부 구현)을 고르지 않았다(2026-11-25 UTC).
- `libs/java-security` `IamClientCredentialsTokenProvider` 의 `RestClient.builder()` — iam 토큰 엔드포인트로만 가고 iam 은 tracer 가 없어 전파해도 이어 받을 쪽이 없다. 가드 모집단(tracer 서비스의 `src/main`)에도 들지 않는다(`libs/`). Evidence 에 이 판단을 적는다.
- iam · wms 둘 다 `spring.application.name: admin-service` 라 trace 에서 구별되지 않는 문제 — 이름 변경은 대시보드 · 알림 · 라우팅에 번지는 별도 결정.
- federation e2e 의 overview trace 에 `scm-platform-procurement-service` 가 ingested 인데도 그 trace 에는 없던 이유 — AC-0 (b) 가 gateway 쪽을 배제하면 남는 원인은 이 티켓에서 재지 않는다(Evidence 에 «미측정» 으로).

# Acceptance Criteria

- **AC-0**: § In Scope 1 (a)(b)(c) 결과가 Implementation Evidence 에 수치 · 인용과 함께 있다.
- **AC-1**: `ResilienceClientFactory` 에 레지스트리 없는 `buildRestClient` 오버로드가 0개. `git grep -n "buildRestClient(" -- 'projects/**/src/main/**' 'libs/**/src/main/**'` 의 모든 호출이 레지스트리를 넘긴다.
- **AC-2**: `libs/java-common` 테스트가 **실제 전파를 잰다** — OTel bridge tracer(테스트 범위 의존성)를 붙인 `ObservationRegistry` 로 만든 클라이언트가 로컬 HTTP 서버에 보낸 요청에 `traceparent` 헤더가 있고, `ObservationRegistry.NOOP` 으로 만든 클라이언트의 요청에는 없다(대조군). 두 단언 모두 bite 를 증명한다.
- **AC-3**: «팩토리 밖» 6개 파일(AC-0 (a) 재측정 수)이 `observationRegistry(` 를 건다.
- **AC-4**: 재발 가드가 `ci.yml` 에 배선되고, 6개 중 한 줄을 되돌리면 빨강 · 원복하면 초록(bite 기록).
- **AC-5**: `platform/observability.md` § Tracing 이 In Scope 6 대로 바뀌었고, 프로젝트 · 서비스 이름이 없다.
- **AC-6**: 영향받는 모든 모듈의 테스트가 CI 에서 초록(impl PR 의 `statusCheckRollup`) — ecommerce · iam · erp · fan · finance · scm 의 Integration 레인 포함.

# Related Specs

- `platform/observability.md` § Tracing · § Spec ↔ Code ↔ Consumers
- `platform/shared-library-policy.md` (공용 lib 시그니처 변경 — 호출자 적응은 같은 PR)
- `docs/adr/ADR-MONO-007a-trace-layer.md` (trace 백엔드 = VictoriaTraces)

# Related Contracts

- 없음 — HTTP API · 이벤트 계약 변경 없음(요청 헤더 `traceparent` · `tracestate` 추가는 W3C 표준 전파이며 계약 필드가 아니다).

# Edge Cases

- 테스트가 `new XClient(...)` 로 만드는 클래스 — 생성자 인자가 늘어 컴파일이 깨진다 → `ObservationRegistry.NOOP`.
- 외부 서드파티(Slack · FCM · 공급사 · 환율)로도 `traceparent` 가 나간다 — W3C 표준 헤더이고 trace ID 외 정보는 없다. 거부하는 서드파티가 있으면 그 클라이언트만 예외로 하고 Evidence 에 적는다.
- `http.client.requests` 의 `uri` 태그 — 문자열 결합으로 URI 를 만드는 호출은 카디널리티가 커진다(AC-0 (c) 상한 확인).
- ecommerce(agent)에서 Micrometer 관측이 함께 돌아도 tracer 가 없으므로 span 이 겹치지 않는다 — AC-0 (c) 에서 runtimeClasspath 로 확인.

# Failure Scenarios

- 옛 오버로드를 남겨 두면 → 새 호출자가 조용히 전파를 잃는다(이 티켓이 지우는 이유).
- 가드의 술어가 «파일에 `ObservationRegistry` 라는 글자가 있다» 수준이면 → import 만 하고 빌더에 걸지 않은 파일이 통과한다. 빌더 체인 단위로 문다.
- AC-2 가 `TestObservationRegistry` 로 «관측이 시작됐다» 만 재면 → 헤더 주입(전파)은 재지 않는다. 받는 쪽 서버에서 헤더를 읽어야 한다.
- Boot `RestClient.Builder` 로 바꾸면 → 직렬화 설정이 바뀌어 계약 테스트가 깨지거나, 더 나쁘게는 통과한 채 응답 형식이 달라진다(In Scope 2 가 막는 이유).
