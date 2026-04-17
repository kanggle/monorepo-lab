package com.gap.security.redis;

/**
 * Provides standardized Redis key construction for security-related keys.
 * <p>
 * This is an interface only. Each service provides its own implementation
 * in the infrastructure layer based on its specific key namespace and schema.
 * <p>
 * Key patterns follow the conventions in specs/services/auth-service/redis-keys.md.
 */
public interface RedisKeyHelper {

    /**
     * Builds a namespaced Redis key from the given parts.
     *
     * @param parts key segments to join with the separator
     * @return the fully qualified Redis key
     */
    String buildKey(String... parts);

    /**
     * Returns the namespace prefix used for all keys from this helper.
     *
     * @return the namespace string
     */
    String namespace();
}
