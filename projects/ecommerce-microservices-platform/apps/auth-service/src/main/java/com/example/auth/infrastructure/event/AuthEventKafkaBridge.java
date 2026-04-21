package com.example.auth.infrastructure.event;

import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Spring {@link AuthEvent} 를 Kafka 토픽으로 발행하는 브리지.
 *
 * <p>회원가입/OAuth 가입 트랜잭션이 커밋된 이후에 실행(AFTER_COMMIT)되며,
 * {@code fallbackExecution = true} 이므로 활성 트랜잭션이 없는 경우
 * (예: republish 배치, 단위 테스트) 에도 동일하게 동작한다.</p>
 *
 * <p>발행 실패 시 signup 흐름을 깨지 않도록 예외를 삼키고 메트릭으로만 표출한다.</p>
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class AuthEventKafkaBridge {

    static final String USER_SIGNED_UP_TOPIC = "auth.user.signed-up";

    /** payload class -> kafka topic */
    private static final Map<Class<?>, String> TOPIC_MAPPING = Map.of(
            UserSignedUp.class, USER_SIGNED_UP_TOPIC
    );

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuthMetricsRecorder authMetrics;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(AuthEvent event) {
        String topic = resolveTopic(event.payload());
        if (topic == null) {
            // 아직 매핑되지 않은 이벤트 타입은 Kafka 로 보내지 않는다.
            log.debug("No Kafka topic mapping for eventType={}, skipping", event.eventType());
            return;
        }

        String key = event.eventId().toString();
        String message;
        try {
            message = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Event serialization failed: eventType={}, eventId={}", event.eventType(), event.eventId(), e);
            authMetrics.incrementEventPublishFailure(event.eventType());
            return;
        }

        try {
            kafkaTemplate.send(topic, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Event publishing failed: eventType={}, topic={}, eventId={}",
                                    event.eventType(), topic, event.eventId(), ex);
                            authMetrics.incrementEventPublishFailure(event.eventType());
                        } else {
                            log.info("Published {} event to {} (eventId={})",
                                    event.eventType(), topic, event.eventId());
                        }
                    });
        } catch (RuntimeException e) {
            // send 호출 자체에서 발생하는 동기 예외 (예: Kafka 비활성 상태)
            log.error("Event publishing failed synchronously: eventType={}, topic={}, eventId={}",
                    event.eventType(), topic, event.eventId(), e);
            authMetrics.incrementEventPublishFailure(event.eventType());
        }
    }

    private String resolveTopic(Object payload) {
        if (payload == null) {
            return null;
        }
        return TOPIC_MAPPING.get(payload.getClass());
    }
}
