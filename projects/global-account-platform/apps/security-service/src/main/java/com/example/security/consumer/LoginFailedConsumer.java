package com.example.security.consumer;

import com.example.security.application.DetectSuspiciousActivityUseCase;
import com.example.security.application.RecordLoginHistoryUseCase;
import com.example.security.consumer.handler.EventDedupService;
import com.example.security.domain.history.LoginOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoginFailedConsumer extends AbstractAuthEventConsumer {

    public LoginFailedConsumer(ObjectMapper objectMapper,
                                EventDedupService dedupService,
                                RecordLoginHistoryUseCase recordLoginHistoryUseCase,
                                DetectSuspiciousActivityUseCase detectUseCase) {
        super(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    @KafkaListener(topics = "auth.login.failed", groupId = "security-service")
    public void onMessage(ConsumerRecord<String, String> record) {
        processEvent(record, LoginOutcome.FAILURE);
    }

    @Override
    protected LoginOutcome resolveOutcome(JsonNode envelope, LoginOutcome defaultOutcome) {
        return AuthEventMapper.resolveFailureOutcome(envelope.path("payload"));
    }
}
