---
name: gateway-security
description: API gateway JWT filter, routing, header enrichment
category: backend
---

# Skill: Gateway Security

Patterns for API gateway JWT validation, routing, and request enrichment using Spring Cloud Gateway.

Prerequisite: read `platform/security-rules.md` and `platform/api-gateway-policy.md` before using this skill.

---

## JWT Authentication Filter (GlobalFilter)

Validates JWT on protected routes, enriches requests with user context headers.

```java
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -100; // runs before other filters

    private final JwtParser jwtParser;
    private final RouteService routeService;
    private final GatewayMetrics gatewayMetrics;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Strip spoofed headers
        ServerHttpRequest stripped = stripSpoofHeaders(request);

        // 2. Public routes pass through
        if (routeService.isPublicRoute(request.getMethod(), path)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // 3. Extract and validate JWT
        String authHeader = stripped.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            gatewayMetrics.incrementJwtValidationFailure("missing");
            return writeUnauthorized(exchange, "Access token is required");
        }

        // 4. Parse claims and enrich request
        Claims claims = jwtParser.parseSignedClaims(authHeader.substring(7)).getPayload();
        ServerHttpRequest enriched = stripped.mutate()
            .header("X-User-Id", claims.getSubject())
            .header("X-User-Email", claims.get("email", String.class))
            .header("X-User-Role", claims.get("role", String.class))
            .build();

        return chain.filter(exchange.mutate().request(enriched).build());
    }
}
```

---

## Spoof Header Stripping

Always remove user context headers from incoming requests before JWT processing.

```java
private ServerHttpRequest stripSpoofHeaders(ServerHttpRequest request) {
    return request.mutate()
        .headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Email");
            h.remove("X-User-Role");
        })
        .build();
}
```

---

## Route Service

Defines public routes and resolves target services from paths.

```java
@Service
public class RouteService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public boolean isPublicRoute(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method)) {
            if ("/api/auth/signup".equals(path)) return true;
            if ("/api/auth/login".equals(path)) return true;
            if ("/api/auth/refresh".equals(path)) return true;
        }
        if (HttpMethod.GET.equals(method)) {
            if (PATH_MATCHER.match("/api/products/**", path)) return true;
            if (PATH_MATCHER.match("/api/search/**", path)) return true;
        }
        return false;
    }

    public String resolveTargetService(String path) {
        if (path.startsWith("/api/auth")) return "example-service";
        if (path.startsWith("/api/products")) return "example-service";
        if (path.startsWith("/api/orders")) return "example-service";
        // ...
        return "unknown";
    }
}
```

---

## Request Logging Filter

Logs request latency and records metrics for rate limiting and upstream errors.

```java
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -99; // runs after JWT filter

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(signal -> {
            long latency = System.currentTimeMillis() - startTime;
            int status = exchange.getResponse().getStatusCode().value();
            log.info("{} {} {} {}ms", request.getMethod(), request.getPath(), status, latency);

            if (status == 429) gatewayMetrics.incrementRateLimited(targetService);
            if (status >= 500) gatewayMetrics.incrementUpstreamError(targetService);
        });
    }
}
```

---

## Gateway Rate Limiting

Uses Spring Cloud Gateway's built-in `RequestRateLimiter` with IP-based key resolver.

```java
@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
        );
    }
}
```

---

## Filter Ordering

| Order | Filter | Purpose |
|---|---|---|
| -100 | JwtAuthenticationFilter | Auth validation + header enrichment |
| -99 | RequestLoggingFilter | Latency logging + error metrics |

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Not stripping spoof headers | Always remove `X-User-*` headers before processing |
| Public route check after JWT validation | Check public routes first to avoid unnecessary validation |
| Blocking calls in reactive filter | Use reactive operators — no blocking I/O |
| Missing metrics for JWT failures | Record failure reason (missing, expired, invalid) |
| Gateway filter not ordered | Implement `Ordered` and return explicit order value |
