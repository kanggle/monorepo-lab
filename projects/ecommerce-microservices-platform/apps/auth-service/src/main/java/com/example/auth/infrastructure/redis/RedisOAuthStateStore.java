package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.OAuthStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!standalone")
public class RedisOAuthStateStore implements OAuthStateStore {

    private final StringRedisTemplate redisTemplate;
    private final String statePrefix;

    public RedisOAuthStateStore(StringRedisTemplate redisTemplate,
                                @Value("${app.redis.key-namespace:auth}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.statePrefix = namespace + ":oauth:state:";
    }

    @Override
    public void save(String state, String callbackUrl, Duration ttl) {
        redisTemplate.opsForValue().set(statePrefix + state, callbackUrl, ttl);
    }

    @Override
    public Optional<String> getAndDelete(String state) {
        String key = statePrefix + state;
        String value = redisTemplate.opsForValue().getAndDelete(key);
        return Optional.ofNullable(value);
    }
}
