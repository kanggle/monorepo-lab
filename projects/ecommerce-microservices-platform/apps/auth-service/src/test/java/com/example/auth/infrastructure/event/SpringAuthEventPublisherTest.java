package com.example.auth.infrastructure.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringAuthEventPublisher 단위 테스트")
class SpringAuthEventPublisherTest {

    @InjectMocks
    private SpringAuthEventPublisher publisher;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private AuthMetrics authMetrics;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUpLogCapture() {
        logger = (Logger) LoggerFactory.getLogger(SpringAuthEventPublisher.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("publish()는 ApplicationEventPublisher.publishEvent()에 위임한다")
    void publish_delegatesToApplicationEventPublisher() {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동"));

        publisher.publish(event);

        then(applicationEventPublisher).should().publishEvent(event);
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 event_publish_failure_total 메트릭이 증가한다")
    void publish_failure_incrementsEventPublishFailureMetric() {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동"));
        willThrow(new RuntimeException("Event listener error")).given(applicationEventPublisher).publishEvent(any());

        publisher.publish(event);

        then(authMetrics).should().incrementEventPublishFailure("UserSignedUp");
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 ERROR 레벨로 로그가 기록된다")
    void publish_failure_logsAtErrorLevel() {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동"));
        willThrow(new RuntimeException("Event listener error")).given(applicationEventPublisher).publishEvent(any());

        publisher.publish(event);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event publishing failed")
                        && e.getFormattedMessage().contains("UserSignedUp"));
    }
}
