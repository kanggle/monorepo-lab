package com.example.security.consumer.handler;

import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.example.security.infrastructure.redis.RedisEventDedupStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventDedupServiceUnitTest {

    @Mock
    private RedisEventDedupStore redisStore;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @InjectMocks
    private EventDedupService dedupService;

    @Test
    @DisplayName("Returns true when Redis has the event (fast path)")
    void redisDedupHit() {
        when(redisStore.isDuplicate("evt-001")).thenReturn(true);

        assertThat(dedupService.isDuplicate("evt-001")).isTrue();
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    @DisplayName("Falls back to MySQL when Redis misses, returns true if found in DB")
    void mysqlFallbackHit() {
        when(redisStore.isDuplicate("evt-002")).thenReturn(false);
        when(processedEventRepository.existsByEventId("evt-002")).thenReturn(true);

        assertThat(dedupService.isDuplicate("evt-002")).isTrue();
        verify(redisStore).markProcessed("evt-002");
    }

    @Test
    @DisplayName("Returns false when event not found in either Redis or MySQL")
    void noDedup() {
        when(redisStore.isDuplicate("evt-003")).thenReturn(false);
        when(processedEventRepository.existsByEventId("evt-003")).thenReturn(false);

        assertThat(dedupService.isDuplicate("evt-003")).isFalse();
    }

    @Test
    @DisplayName("markProcessedInRedis saves to Redis only")
    void markProcessedInRedis() {
        dedupService.markProcessedInRedis("evt-004");

        verify(redisStore).markProcessed("evt-004");
        verifyNoInteractions(processedEventRepository);
    }
}
