package com.example.auth.infrastructure.email;

import com.example.auth.application.exception.EmailSendException;
import com.example.auth.application.port.EmailSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TASK-MONO-770 (owner decision 2026-10-07 — «same gap, same mechanism») — the password-reset mail actually
 * leaves.
 *
 * <p>The twin of account-service's {@code SmtpEmailVerificationNotifier}: one generic SMTP adapter over
 * {@link JavaMailSender}, configured by the standard {@code spring.mail.*} keys, selected by
 * {@code iam.mail.enabled=true} — a property, not a profile (the demo runs IAM under {@code e2e}). With mail off,
 * {@link LoggingEmailSender} is wired outside {@code prod}, and nothing in {@code prod} — the context fails fast
 * (TASK-BE-242's guarantee kept). Enabled without {@code spring.mail.host} fails fast too: Spring Boot then
 * creates no {@link JavaMailSender}.
 *
 * <p>Port contract ({@link EmailSenderPort}): every send-path failure is wrapped in {@link EmailSendException},
 * which {@code RequestPasswordResetUseCase} absorbs — the endpoint answers 204 regardless, so it cannot reveal
 * whether the address exists.
 *
 * <p>R4 ({@code rules/traits/regulated.md}): the token goes into the mail body only — never a log line, never an
 * exception message. The recipient is logged masked. The wrapped exception carries a fixed message and <b>no
 * cause</b> (SMTP error text can quote the address, and the caller logs {@code e.getMessage()}).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "iam.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSenderPort {

    static final String SUBJECT = "[IAM] 비밀번호 재설정 안내";

    private final JavaMailSender mailSender;
    private final String from;
    private final String linkBaseUrl;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${iam.mail.from}") String from,
            @Value("${iam.mail.password-reset-link-base-url}") String linkBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText(body(link(resetToken)));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Password reset email failed — to={} type={}",
                    LoggingEmailSender.mask(toEmail), e.getClass().getSimpleName());
            throw new EmailSendException("password reset email was not sent");
        }
        log.info("Password reset email sent — to={}", LoggingEmailSender.mask(toEmail));
    }

    /**
     * TASK-MONO-771 (owner decision OD-4) — the «new second factor enrolled» notice. No secret, no code, no
     * acting link in the body (R4). Same failure wrapping as the reset mail: fixed message, no cause.
     */
    @Override
    public void sendSecondFactorEnrolledNotice(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(MFA_ENROLLED_SUBJECT);
        message.setText(MFA_ENROLLED_BODY);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Second-factor enrollment notice failed — to={} type={}",
                    LoggingEmailSender.mask(toEmail), e.getClass().getSimpleName());
            throw new EmailSendException("second-factor enrollment notice was not sent");
        }
        log.info("Second-factor enrollment notice sent — to={}", LoggingEmailSender.mask(toEmail));
    }

    static final String MFA_ENROLLED_SUBJECT = "[IAM] 새 2단계 인증 수단이 등록되었습니다";

    static final String MFA_ENROLLED_BODY = """
            안녕하세요.

            방금 이 계정에 새 2단계 인증 수단(인증 앱)이 등록되었습니다.
            본인이 등록했다면 이 메일은 무시하셔도 됩니다.

            본인이 아니라면 비밀번호를 바로 재설정하고, 관리자에게 2단계 인증 초기화를 요청하세요.
            """;

    String link(String token) {
        return UriComponentsBuilder.fromUriString(linkBaseUrl)
                .queryParam("token", token)
                .encode()
                .toUriString();
    }

    private static String body(String link) {
        return """
                안녕하세요.

                비밀번호 재설정을 요청하셨습니다. 아래 링크에서 새 비밀번호를 정하세요.
                %s

                링크는 1시간 동안 한 번만 쓸 수 있습니다.
                요청한 적이 없다면 이 메일을 무시하세요 — 비밀번호는 바뀌지 않습니다.
                """.formatted(link);
    }
}
