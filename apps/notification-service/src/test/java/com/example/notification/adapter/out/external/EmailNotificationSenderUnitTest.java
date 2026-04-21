package com.example.notification.adapter.out.external;

import com.example.notification.domain.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationSender 단위 테스트")
class EmailNotificationSenderUnitTest {

    @InjectMocks
    private EmailNotificationSender emailNotificationSender;

    @Mock
    private JavaMailSender mailSender;

    @Test
    @DisplayName("이메일을 정상적으로 발송하면 수신자, 제목, 본문이 올바르게 설정된다")
    void send_validParameters_sendsEmailWithCorrectFields() {
        ReflectionTestUtils.setField(emailNotificationSender, "fromAddress", "noreply@example.com");

        emailNotificationSender.send("user@example.com", "주문 확인", "주문이 완료되었습니다.");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("주문 확인");
        assertThat(message.getText()).isEqualTo("주문이 완료되었습니다.");
    }

    @Test
    @DisplayName("JavaMailSender 전송 실패 시 예외가 전파된다")
    void send_mailSenderFails_propagatesException() {
        ReflectionTestUtils.setField(emailNotificationSender, "fromAddress", "noreply@example.com");
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                emailNotificationSender.send("user@example.com", "제목", "본문"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMTP connection failed");
    }

    @Test
    @DisplayName("지원하는 채널이 EMAIL이다")
    void supportedChannel_returnsEmail() {
        assertThat(emailNotificationSender.supportedChannel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
