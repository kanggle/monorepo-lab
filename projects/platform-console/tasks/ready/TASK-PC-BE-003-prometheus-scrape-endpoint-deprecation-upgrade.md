# Task ID

TASK-PC-BE-003

# Title

console-bff — `PrometheusScrapeEndpoint(PrometheusRegistry)` deprecated 생성자 upgrade → `(PrometheusRegistry, Properties)` (TASK-PC-BE-001 § Honest gaps (b) closure)

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **closes (explicit)**: TASK-PC-BE-001 § Honest gaps **(b)** — *"`PrometheusScrapeEndpoint` constructor used is deprecated since Spring Boot 3.3.1 — TASK-PC-FE-011 (first composition route) is the natural upgrade window."* FE-011 머지됐지만 deprecation upgrade 는 누락 — 본 task 가 약속 이행.
- **no spec / contract / ADR change**: production-internal mechanical upgrade. 모든 ADR / contract / specs / 5 producer / console-web byte-unchanged.
- **no behavior change**: `PrometheusScrapeEndpoint` 의 wire behavior 동일 (deprecated 생성자가 새 생성자에 `null` 위임).

# Goal

[`ObservabilityConfig.java:77`](projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/infrastructure/config/ObservabilityConfig.java#L77) 의

```java
new PrometheusScrapeEndpoint(registry.getPrometheusRegistry())
```

은 Spring Boot 3.3.1 부터 deprecated, **3.5.0 에서 removal 예정** (`@Deprecated(since = "3.3.1", forRemoval = true)` — spring-boot-actuator 3.4.1 sources 확인). 본 task 는 새 생성자

```java
new PrometheusScrapeEndpoint(registry.getPrometheusRegistry(), null)
```

로 교체. 두 번째 `Properties` 인수는 추가 exporter properties 용 — `null` 시 새 생성자가 `PrometheusPropertiesLoader.load()` (no-arg) 사용 → deprecated 생성자가 했던 동작과 정확히 동일 (deprecated 생성자 본체 `this(prometheusRegistry, null)` 위임 그대로). 따라서 **wire behavior 동일, deprecation warning 해소, Spring Boot 3.5 upgrade 차단 풀림**.

TASK-PC-BE-001 § Honest gaps (b) 의 *"TASK-PC-FE-011 (first composition route) is the natural upgrade window"* 약속은 FE-011 머지 동안 누락됐고, FE-012 / BE-002 회귀 saga 가 끝난 지금이 backlog cleanup 의 자연 시점.

# Scope

## In Scope

### Backend (단일 파일 1줄 변경)

- [`apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/infrastructure/config/ObservabilityConfig.java`](projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/infrastructure/config/ObservabilityConfig.java) line 77 — `new PrometheusScrapeEndpoint(registry.getPrometheusRegistry())` → `new PrometheusScrapeEndpoint(registry.getPrometheusRegistry(), null)`.
- Javadoc 의 *"if not, this bean registers it directly"* 부분에 1 문장 추가 — `Properties=null` 의도 ("no exporter property overrides; default {@code PrometheusPropertiesLoader.load()} behavior 그대로").
- `@SuppressWarnings("deprecation")` / `@SuppressWarnings("removal")` 부재 (이제 deprecated 호출 부재).

### Tests

- 기존 unit + slice + IT 모두 GREEN (회귀 0). `/actuator/prometheus` 가 3 mandatory metric families (`bff_fanout_latency_seconds` / `bff_fanout_errors_total` / `bff_aggregation_degrade_count_total`) 그대로 expose.
- `compileJava` 의 `-Xlint:deprecation` 경고 0 (본 변경 적용 시 deprecated call site 사라짐).

## Out of Scope

- `console-integration-contract.md` § 2.4.9 / § 2.4.9.1 spec 변경 — 모든 D1-D8 hard invariant / observability table byte-unchanged.
- ADR-MONO-017 변경 부재 (HARDSTOP-04 discipline).
- Spring Boot 버전 bump — 3.4.1 그대로.
- `Properties` 객체로 exporter properties 추가 — null 로 default loader 유지 (deprecated 생성자가 했던 동작과 정확히 동일).
- `@Bean public PrometheusMeterRegistry` 의 explicit 정의 회수 — TASK-PC-BE-001 § Honest gaps (a) 의 "test profile diagnostic surface retained" 와 동일 의도로 유지 (Spring Boot 3.4 + Micrometer 1.14 + libs/java-observability matrix 의 autoconfig conditional gap mitigation). 별 backlog (BE-001 § honest gap (a)).
- console-web / 5 producer / TS / docs / hooks 모두 byte-unchanged.

# Acceptance Criteria

1. `ObservabilityConfig.java:77` 의 deprecated 생성자 호출이 새 (non-deprecated) 생성자 호출로 교체됨.
2. `./gradlew :projects:platform-console:apps:console-bff:compileJava -Pcompiler.args=-Xlint:deprecation` 시 본 파일 관련 deprecation warning 0.
3. `./gradlew :projects:platform-console:apps:console-bff:test` GREEN (unit + slice 0 regression).
4. self-CI `Integration (platform-console console-bff, ...)` GREEN — `/actuator/prometheus` 가 3 mandatory metric families 그대로 expose (회귀 0).
5. `console-integration-contract.md` § 2.4.9 / § 2.4.9.1 / § 3 attestation count = 16 byte-unchanged.
6. `specs/services/console-bff/architecture.md` + ADR-MONO-017 byte-unchanged.
7. 5 producer apps + console-web byte-unchanged.
8. self-CI 전체 ALL GREEN (회귀 0).

# Related Specs

- `projects/platform-console/specs/services/console-bff/architecture.md` § Observability (D7.A) — 3 mandatory metric families. wire behavior 변경 부재이므로 spec touch 없음.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 observability set — endpoint surface (`/actuator/prometheus`) 변경 부재.

# Edge Cases

- **Spring Boot 3.5.x 이후 deprecated 생성자 removal**: 본 fix 가 그 호환성 차단을 미리 해소.
- **`Properties` 인수에 user-defined exporter override 가 미래에 필요**: 그 시점에 별 task. 현재는 default loader 충분.
- **`@SuppressWarnings("removal")` 가 누적되어 있는지**: 본 파일에는 없음 (Spring Boot autoconfig 의 `@SuppressWarnings("removal")` 는 autoconfig 자체의 것, 본 사용자 코드와 무관).

# Failure Scenarios

| 조건 | 본 PR 의 반응 |
|---|---|
| 새 생성자 호출 후 `/actuator/prometheus` 가 빈 응답 또는 500 | 새 생성자가 deprecated 생성자와 동일 위임 (`this(prometheusRegistry, null)`) → wire behavior 동일. 만약 회귀 발견되면 진단 surface 강화 (Spring Security DEBUG 로그 + IT body capture). STOP. |
| `PrometheusPropertiesLoader.load()` 가 외부 system property 의존 → CI runner 환경 차이 | 두 환경 (local Windows / CI Linux) 모두 PASS — system property 부재 시 default 사용. local + CI 검증. |
| 다른 `*-bff` 또는 `*-platform` 의 같은 deprecated 생성자 사용 발견 | sweep 1회 (Grep) 후 동일 fix. 본 PR scope 안에서 일괄 처리하거나 별 task. |

---

# Implementation Notes (impl PR 단계 reference)

```java
@Bean
public PrometheusScrapeEndpoint prometheusScrapeEndpoint(PrometheusMeterRegistry registry) {
    // Spring Boot 3.3.1+ non-deprecated constructor. `null` Properties means
    // "use PrometheusPropertiesLoader.load() default" — identical to the
    // deprecated single-arg constructor's internal `this(registry, null)` delegate.
    return new PrometheusScrapeEndpoint(registry.getPrometheusRegistry(), null);
}
```

---

# Approval

- 분석 = Opus 4.7
- 구현 권장 = Sonnet 4.6 (mechanical 1-line upgrade, scope 좁음)
- 리뷰 = Opus 4.7 (dispatcher 독립 재검증 + acceptance criteria 8/8 단언)
