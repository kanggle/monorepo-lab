package com.example.security.service.consumer;

import com.example.security.service.application.DetectSuspiciousActivityUseCase;
import com.example.security.service.application.RecordLoginHistoryUseCase;
import com.example.security.service.consumer.handler.EventDedupService;
import com.example.security.service.domain.history.LoginOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoginSucceededConsumer extends AbstractAuthEventConsumer {

    public LoginSucceededConsumer(ObjectMapper objectMapper,
                                   EventDedupService dedupService,
                                   RecordLoginHistoryUseCase recordLoginHistoryUseCase,
                                   DetectSuspiciousActivityUseCase detectUseCase) {
        super(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    @KafkaListener(topics = "auth.login.succeeded")
    public void onMessage(ConsumerRecord<String, String> record) {
        processEvent(record, LoginOutcome.SUCCESS);
    }
}
