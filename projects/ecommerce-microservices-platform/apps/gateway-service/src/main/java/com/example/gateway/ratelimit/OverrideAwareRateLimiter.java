package com.example.gateway.ratelimit;

import com.example.gateway.config.RateLimitOverrideProperties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

/**
 * Per-tenant rate-limit override resolver (TASK-BE-405 AC-2 — M7 realization,
 * ADR-MONO-030 Step 4 facet e).
 *
 * <p>Spring Cloud Gateway's stock {@link RedisRateLimiter} resolves its
 * {@code replenishRate}/{@code burstCapacity} <b>statically per route id</b>
 * ({@code loadConfiguration(routeId)}); it has no concept of a per-tenant limit. This
 * decorator adds the optional per-tenant override required by AC-2 while preserving the
 * stock limiter's atomic Lua token-bucket:
 *
 * <ol>
 *   <li>The resolved request key ({@code id}) produced by
 *       {@code TenantRouteRateLimitConfig#tenantRouteKeyResolver} encodes the tenant as a
 *       {@code :t:<tenantId>} segment ({@code rate:ecommerce-gw:<routeId>:t:<tenantId>[:ip:<ip>]}).
 *       This decorator parses {@code <tenantId>} back out of the key.</li>
 *   <li>If {@link RateLimitOverrideProperties} has a valid override for
 *       {@code (tenantId, routeId)}, the call is delegated to a small cached
 *       {@link RedisRateLimiter} instance whose static {@code Config} for that route id is
 *       the <b>override</b> limits.</li>
 *   <li>Otherwise the call is delegated to the <b>default</b> {@link RedisRateLimiter} (the
 *       route/config default) — identical to today.</li>
 * </ol>
 *
 * <p><b>Same bucket, different size.</b> {@code RedisRateLimiter.getKeys(id, routeId)} derives
 * the Redis token-bucket key from {@code id} + {@code routeId} only — not from the limiter
 * instance — so the override limiter and the default limiter operate on the <i>same</i>
 * per-{@code (tenant,route)} bucket; only the replenish/burst applied to it differ. This keeps
 * the {@code (tenant, route)} isolation (AC-1) intact for both paths.
 *
 * <h2>Never-null limits / backward compatibility</h2>
 * The override is consulted only when present <em>and</em>
 * {@link RateLimitOverrideProperties.Limit#isValid() valid}; any other case falls through to
 * the default limiter. With no overrides configured the override map is empty and every call
 * hits the default delegate — net-zero versus the config-default-only implementation.
 *
 * <h2>Composition with fail-open</h2>
 * This decorator does not handle Redis failures itself; it only chooses <i>which</i>
 * {@link RedisRateLimiter} computes the decision. Fail-open is layered <b>around</b> this
 * decorator by {@link FailOpenRateLimiter}, so both the override path and the default path
 * inherit identical fail-open semantics (TASK-BE-405 § Degrade).
 */
public class OverrideAwareRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(OverrideAwareRateLimiter.class);

    /** Key segment that precedes the tenant id in the resolved rate-limit key. */
    static final String TENANT_SEGMENT = ":t:";
    /** Key segment that follows the tenant id (anonymous/pre-auth IP qualifier), if present. */
    static final String IP_SEGMENT = ":ip:";

    private final RedisRateLimiter defaultDelegate;
    private final RateLimitOverrideProperties overrideProperties;
    /** Factory that builds an override {@link RedisRateLimiter} seeded with a given route Config. */
    private final Function<RedisRateLimiter.Config, RedisRateLimiter> overrideLimiterFactory;
    /** Cache: {@code <routeId>|<tenantId>} -> override limiter (one per distinct override tuple). */
    private final Map<String, RedisRateLimiter> overrideLimiterCache = new ConcurrentHashMap<>();

    public OverrideAwareRateLimiter(RedisRateLimiter defaultDelegate,
                                    RateLimitOverrideProperties overrideProperties,
                                    Function<RedisRateLimiter.Config, RedisRateLimiter> overrideLimiterFactory) {
        this.defaultDelegate = defaultDelegate;
        this.overrideProperties = overrideProperties;
        this.overrideLimiterFactory = overrideLimiterFactory;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        String tenantId = parseTenant(id);
        RateLimitOverrideProperties.Limit override = overrideProperties.resolve(tenantId, routeId);
        if (override == null || !override.isValid()) {
            // No (or invalid) override → route/config default. Backward-compatible default path.
            return defaultDelegate.isAllowed(routeId, id);
        }
        RedisRateLimiter overrideLimiter = overrideLimiterCache.computeIfAbsent(
                cacheKey(routeId, tenantId), k -> buildOverrideLimiter(routeId, override));
        if (log.isDebugEnabled()) {
            log.debug("Applying per-tenant rate-limit override for tenant='{}' route='{}': {}",
                    tenantId, routeId, override);
        }
        return overrideLimiter.isAllowed(routeId, id);
    }

    /**
     * Builds (once, cached) a {@link RedisRateLimiter} whose static {@code Config} for this
     * route id is the override limits. It shares the default delegate's
     * {@code ReactiveStringRedisTemplate} + Lua script (so it writes the same bucket), but
     * applies the override replenish/burst.
     */
    private RedisRateLimiter buildOverrideLimiter(String routeId, RateLimitOverrideProperties.Limit override) {
        RedisRateLimiter.Config config = new RedisRateLimiter.Config()
                .setReplenishRate(override.getReplenishRate())
                .setBurstCapacity(override.getBurstCapacity());
        RedisRateLimiter limiter = overrideLimiterFactory.apply(config);
        // Seed the per-route config so loadConfiguration(routeId) resolves the override.
        limiter.getConfig().put(routeId, config);
        return limiter;
    }

    /**
     * Extracts {@code <tenantId>} from {@code ...:t:<tenantId>} (or
     * {@code ...:t:<tenantId>:ip:<ip>}). Returns {@code null} when the key carries no tenant
     * segment — the caller then resolves no override (default path), never a null limit.
     */
    static String parseTenant(String id) {
        if (id == null) {
            return null;
        }
        int t = id.lastIndexOf(TENANT_SEGMENT);
        if (t < 0) {
            return null;
        }
        int start = t + TENANT_SEGMENT.length();
        int ip = id.indexOf(IP_SEGMENT, start);
        String tenant = (ip < 0) ? id.substring(start) : id.substring(start, ip);
        return tenant.isBlank() ? null : tenant;
    }

    private static String cacheKey(String routeId, String tenantId) {
        return routeId + "|" + tenantId;
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return defaultDelegate.getConfig();
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return defaultDelegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return defaultDelegate.newConfig();
    }

    /** Visible for tests — the set of override tuples already materialised. */
    List<String> cachedOverrideKeys() {
        return List.copyOf(overrideLimiterCache.keySet());
    }
}
