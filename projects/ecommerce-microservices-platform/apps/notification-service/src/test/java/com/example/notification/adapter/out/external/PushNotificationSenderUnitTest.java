package com.example.notification.adapter.out.external;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.notification.domain.model.NotificationChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("PushNotificationSender 단위 테스트")
class PushNotificationSenderUnitTest {

    private PushNotificationSender pushNotificationSender;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        pushNotificationSender = new PushNotificationSender();

        logger = (Logger) LoggerFactory.getLogger(PushNotificationSender.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("지원하는 채널이 PUSH이다")
    void supportedChannel_returnsPush() {
        assertThat(pushNotificationSender.supportedChannel()).isEqualTo(NotificationChannel.PUSH);
    }

    @Test
    @DisplayName("발송 시 예외 없이 완료되고 수신자·제목을 담은 INFO 로그를 남긴다")
    void send_validParameters_logsAtInfoWithoutThrowing() {
        assertThatCode(() ->
                pushNotificationSender.send("user-123", "주문 확인", "주문이 완료되었습니다."))
                .doesNotThrowAnyException();

        assertThat(logAppender.list)
                .hasSize(1)
                .allSatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("user-123")
                            .contains("주문 확인");
                });
    }

    @Test
    @DisplayName("빈 문자열 인자로도 예외 없이 stub 발송이 완료된다")
    void send_blankArguments_doesNotThrow() {
        assertThatCode(() -> pushNotificationSender.send("", "", ""))
                .doesNotThrowAnyException();

        assertThat(logAppender.list).hasSize(1);
    }
}
