package com.example.account.infrastructure.notifier;

import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.port.EmailVerificationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TASK-MONO-770 (ADR-MONO-080 D3) — the verification mail actually leaves.
 *
 * <p>One generic SMTP adapter over Spring's {@link JavaMailSender}, configured by the standard
 * {@code spring.mail.*} keys (host · port · username · password · properties). Any SMTP endpoint works by
 * configuration alone — Mailpit in the demo, e.g. the AWS SES SMTP interface in production. No provider SDK.
 *
 * <h3>Selected by a property, not a profile</h3>
 *
 * <p>{@code iam.mail.enabled=true} → this bean; otherwise {@link LoggingEmailVerificationNotifier} outside
 * {@code prod}, and nothing in {@code prod} (the context fails fast — TASK-BE-236's guarantee kept). The demo
 * runs IAM under the {@code e2e} profile, so a {@code @Profile("prod")} adapter would never run there.
 *
 * <p>Enabled but mis-configured fails fast too: without {@code spring.mail.host} Spring Boot creates no
 * {@link JavaMailSender}, and this constructor cannot be satisfied.
 *
 * <h3>R4 ({@code rules/traits/regulated.md})</h3>
 *
 * <p>The token goes into the mail body and nowhere else — never a log line, never an exception message. The
 * recipient is logged masked ({@link RecipientMask}). A failure is rethrown as {@link EmailDeliveryException}
 * with a <b>fixed</b> message and <b>no cause</b>: SMTP error text routinely quotes the rejected address, and a
 * cause would carry it into any caller that logs the stack.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "iam.mail", name = "enabled", havingValue = "true")
public class SmtpEmailVerificationNotifier implements EmailVerificationNotifier {

    static final String SUBJECT = "[IAM] 이메일 주소를 인증해 주세요";

    private final JavaMailSender mailSender;
    private final String from;
    private final String linkBaseUrl;

    public SmtpEmailVerificationNotifier(
            JavaMailSender mailSender,
            @Value("${iam.mail.from}") String from,
            @Value("${iam.mail.verification-link-base-url}") String linkBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText(body(link(token)));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            EmailDeliveryException.Kind kind = SmtpFailureClassifier.classify(e);
            // Type only — the exception's message may quote the address (R4).
            log.warn("Verification email failed — to={} kind={} type={}",
                    RecipientMask.mask(toEmail), kind, e.getClass().getSimpleName());
            throw new EmailDeliveryException(kind, "verification email was not sent (" + kind + ")");
        }
        log.info("Verification email sent — to={}", RecipientMask.mask(toEmail));
    }

    String link(String token) {
        return UriComponentsBuilder.fromUriString(linkBaseUrl)
                .queryParam("token", token)
                .encode()
                .toUriString();
    }

    private static String body(String link) {
        return """
                안녕하세요.

                아래 링크를 열고 «이메일 인증 완료» 를 누르면 이 주소가 인증됩니다.
                %s

                링크는 24시간 동안 한 번만 쓸 수 있습니다.
                요청한 적이 없다면 이 메일을 무시하세요 — 아무것도 바뀌지 않습니다.
                """.formatted(link);
    }
}
