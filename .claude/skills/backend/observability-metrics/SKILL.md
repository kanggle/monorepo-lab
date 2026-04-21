---
name: observability-metrics
description: Micrometer business metrics, OTel tracing, MDC
category: backend
---

# Skill: Observability & Metrics

Patterns for Micrometer business metrics and OpenTelemetry tracing integration.

Prerequisite: read `specs/platform/observability.md` before using this skill.

---

## Shared Observability Library

`libs/java-observability` provides auto-configuration for all services.

### Auto-Configuration

```java
@AutoConfiguration
public class ObservabilityAutoConfig {

    // Servlet apps: MDC trace filter
    @Configuration
    @ConditionalOnWebApplication(type = SERVLET)
    static class ServletFilterConfig {
        @Bean
        public MdcTraceFilter mdcTraceFilter() { return new MdcTraceFilter(); }
    }

    // Reactive apps (gateway): WebFilter version
    @Configuration
    @ConditionalOnWebApplication(type = REACTIVE)
    static class ReactiveFilterConfig {
        @Bean
        public MdcTraceWebFilter mdcTraceWebFilter() { return new MdcTraceWebFilter(); }
    }

    // Common tags: service name on all metrics
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerCommonTagsConfig {
        @Bean
        public MeterFilter commonTagsMeterFilter(@Value("${spring.application.name}") String name) {
            return MeterFilter.commonTags(Tags.of("service", name));
        }
    }
}
```

### MDC Trace Filter

Extracts OpenTelemetry trace ID into SLF4J MDC for log correlation.

```java
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        try {
            SpanContext ctx = Span.current().getSpanContext();
            MDC.put("traceId", ctx.isValid() ? ctx.getTraceId() : "");
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

---

## Business Metrics Pattern

Each service defines a metrics port interface and a Micrometer implementation.

### Metrics Port (Domain)

```java
// domain/service/AuthMetricsRecorder.java
public interface AuthMetricsRecorder {
    void incrementSignup();
    void incrementLoginSuccess();
    void incrementLoginFailure(String reason);
    void incrementLogout();
    void incrementTokenRefreshSuccess();
    void incrementTokenRefreshFailure();
    void incrementSessionEviction();
}
```

### Micrometer Implementation (Infrastructure)

```java
@Component
public class AuthMetrics implements AuthMetricsRecorder {

    private final Counter loginSuccessTotal;
    private final Counter loginFailureTotal;
    // ...

    public AuthMetrics(MeterRegistry registry) {
        this.loginSuccessTotal = Counter.builder("auth_login_total")
            .tag("result", "success")
            .register(registry);
        this.loginFailureTotal = Counter.builder("auth_login_total")
            .tag("result", "failure")
            .register(registry);
    }

    @Override
    public void incrementLoginSuccess() { loginSuccessTotal.increment(); }

    @Override
    public void incrementLoginFailure(String reason) {
        loginFailureTotal.increment();
        // also increment reason-specific counter
    }
}
```

---

## Metric Naming Convention

| Metric | Tags | Service |
|---|---|---|
| `auth_login_total` | result=[success\|failure] | auth-service |
| `auth_login_failure_total` | reason=[invalid_credentials\|rate_limited] | auth-service |
| `order_placed_total` | — | order-service |
| `order_cancelled_total` | reason=[user\|system\|payment_failed] | order-service |
| `event_publish_failure_total` | service, event_type | all services |
| `gateway_jwt_validation_failure_total` | reason=[missing\|expired\|invalid] | gateway |
| `gateway_requests_routed_total` | target | gateway |

### Shared Metric Names

```java
// libs/java-observability
public final class EventMetricNames {
    public static final String EVENT_PUBLISH_FAILURE_TOTAL = "event_publish_failure_total";
    public static final String TAG_EVENT_TYPE = "event_type";
    public static final String TAG_SERVICE = "service";
}
```

---

## Counter Construction Pattern

Pre-register counters in the constructor. Use `registry.counter()` for dynamic tags.

```java
// Static tags — pre-register in constructor
this.loginSuccess = Counter.builder("auth_login_total")
    .tag("result", "success")
    .register(registry);

// Dynamic tags — create on demand
public void incrementEventPublishFailure(String eventType) {
    registry.counter(EVENT_PUBLISH_FAILURE_TOTAL,
        TAG_SERVICE, "auth-service",
        TAG_EVENT_TYPE, eventType
    ).increment();
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Creating new Counter on every call | Pre-register counters in constructor for static tags |
| Unbounded dynamic tag values | Use fixed enums for tags — unbounded tags cause memory leaks |
| Missing `service` tag | Use `ObservabilityAutoConfig` common tags |
| MDC not cleared after request | Use `try/finally` to remove MDC keys |
