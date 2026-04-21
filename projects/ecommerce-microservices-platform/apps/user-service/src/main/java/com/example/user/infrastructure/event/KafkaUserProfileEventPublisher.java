package com.example.user.infrastructure.event;

import com.example.user.application.event.UserProfileUpdatedSpringEvent;
import com.example.user.application.event.UserWithdrawnSpringEvent;
import com.example.user.infrastructure.metrics.UserMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class KafkaUserProfileEventPublisher {

    private static final String PROFILE_UPDATED_TOPIC = "user.user-profile.updated";
    private static final String USER_WITHDRAWN_TOPIC = "user.user-withdrawn";
    private static final String EVENT_TYPE_PROFILE_UPDATED = "UserProfileUpdated";
    private static final String EVENT_TYPE_USER_WITHDRAWN = "UserWithdrawn";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserMetrics userMetrics;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProfileUpdated(UserProfileUpdatedSpringEvent springEvent) {
        var event = new UserProfileUpdatedEvent(
                UUID.randomUUID(),
                EVENT_TYPE_PROFILE_UPDATED,
                Instant.now(),
                "user-service",
                new UserProfileUpdatedEvent.Payload(
                        springEvent.userId(),
                        springEvent.nickname(),
                        springEvent.phone(),
                        springEvent.profileImageUrl(),
                        springEvent.updatedAt()
                )
        );

        publishEvent(PROFILE_UPDATED_TOPIC, EVENT_TYPE_PROFILE_UPDATED, springEvent.userId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserWithdrawn(UserWithdrawnSpringEvent springEvent) {
        var event = new UserWithdrawnEvent(
                UUID.randomUUID(),
                EVENT_TYPE_USER_WITHDRAWN,
                Instant.now(),
                "user-service",
                new UserWithdrawnEvent.Payload(
                        springEvent.userId(),
                        springEvent.withdrawnAt()
                )
        );

        publishEvent(USER_WITHDRAWN_TOPIC, EVENT_TYPE_USER_WITHDRAWN, springEvent.userId(), event);
    }

    private void publishEvent(String topic, String eventType, UUID userId, Object event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, userId.toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Event publishing failed: eventType={}, topic={}, userId={}", eventType, topic, userId, ex);
                            userMetrics.incrementEventPublishFailure(eventType);
                        } else {
                            log.info("Published {} event for userId={}", eventType, userId);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Event serialization failed: eventType={}, topic={}, userId={}", eventType, topic, userId, e);
            userMetrics.incrementEventPublishFailure(eventType);
        }
    }
}
