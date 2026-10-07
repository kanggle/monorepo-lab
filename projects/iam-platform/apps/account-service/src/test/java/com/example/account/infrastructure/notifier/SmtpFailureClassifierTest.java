package com.example.account.infrastructure.notifier;

import com.example.account.application.exception.EmailDeliveryException.Kind;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-770 — PERMANENT only on positive evidence that the address is the problem; everything else, and
 * everything unknown, is TRANSIENT.
 */
@DisplayName("SmtpFailureClassifier (TASK-MONO-770)")
class SmtpFailureClassifierTest {

    @Test
    @DisplayName("주소 파싱 실패 → PERMANENT")
    void parse_permanent() {
        assertThat(SmtpFailureClassifier.classify(new MailParseException("bad address"))).isEqualTo(Kind.PERMANENT);
    }

    @Test
    @DisplayName("원인 사슬의 AddressException → PERMANENT")
    void addressExceptionInChain_permanent() {
        MailSendException e = new MailSendException("failed", new MessagingException("wrap", new AddressException("x")));
        assertThat(SmtpFailureClassifier.classify(e)).isEqualTo(Kind.PERMANENT);
    }

    @Test
    @DisplayName("수신자 영구 거부(invalid addresses 있음) → PERMANENT · 일시 거부(valid-unsent 만) → TRANSIENT")
    void sendFailed_invalidVsValidUnsent() throws Exception {
        InternetAddress a = new InternetAddress("a@example.com");
        SendFailedException permanent = new SendFailedException("550", null,
                new InternetAddress[0], new InternetAddress[0], new InternetAddress[]{a});
        SendFailedException temporary = new SendFailedException("451", null,
                new InternetAddress[0], new InternetAddress[]{a}, new InternetAddress[0]);

        assertThat(SmtpFailureClassifier.classify(new MailSendException(Map.of(new Object(), permanent))))
                .isEqualTo(Kind.PERMANENT);
        assertThat(SmtpFailureClassifier.classify(new MailSendException(Map.of(new Object(), temporary))))
                .isEqualTo(Kind.TRANSIENT);
    }

    @Test
    @DisplayName("연결·인증·준비 실패 · 처음 보는 예외 → TRANSIENT (판정 불가 = 일시)")
    void everythingElse_transient() {
        assertThat(SmtpFailureClassifier.classify(new MailSendException("connection refused"))).isEqualTo(Kind.TRANSIENT);
        assertThat(SmtpFailureClassifier.classify(new MailAuthenticationException("535"))).isEqualTo(Kind.TRANSIENT);
        assertThat(SmtpFailureClassifier.classify(new MailPreparationException("io"))).isEqualTo(Kind.TRANSIENT);
    }
}
