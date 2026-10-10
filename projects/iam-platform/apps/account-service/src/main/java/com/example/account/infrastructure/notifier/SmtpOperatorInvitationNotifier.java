package com.example.account.infrastructure.notifier;

import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.port.OperatorInvitationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · OD-4) — the operator-invitation mail actually leaves, through the same
 * generic SMTP path as {@link SmtpEmailVerificationNotifier} (standard {@code spring.mail.*}, Mailpit in the demo).
 * Selected by the same property ({@code iam.mail.enabled=true}); {@link LoggingOperatorInvitationNotifier} is the
 * disjoint complement outside {@code prod}.
 *
 * <p>R4 — exactly the 770 discipline: the token is written into the link in the body and nowhere else; the
 * recipient is logged masked ({@link RecipientMask}); a failure is rethrown as {@link EmailDeliveryException} with
 * a fixed message and no cause (SMTP error text routinely quotes the rejected address), classified by the shared
 * {@link SmtpFailureClassifier} so the two mails cannot disagree on «permanent».
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "iam.mail", name = "enabled", havingValue = "true")
public class SmtpOperatorInvitationNotifier implements OperatorInvitationNotifier {

    static final String SUBJECT = "[IAM] 운영자로 초대되었습니다";
    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final String from;
    private final String linkBaseUrl;

    public SmtpOperatorInvitationNotifier(
            JavaMailSender mailSender,
            @Value("${iam.mail.from}") String from,
            @Value("${iam.mail.operator-invitation-link-base-url}") String linkBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.linkBaseUrl = linkBaseUrl;
    }

    @Override
    public void sendOperatorInvitation(String toEmail, String token, String companyName, String inviterDisplayName,
                                       Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText(body(link(token), companyName, inviterDisplayName, expiresAt));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            EmailDeliveryException.Kind kind = SmtpFailureClassifier.classify(e);
            // Type only — the exception's message may quote the address (R4).
            log.warn("Operator invitation email failed — to={} kind={} type={}",
                    RecipientMask.mask(toEmail), kind, e.getClass().getSimpleName());
            throw new EmailDeliveryException(kind, "operator invitation email was not sent (" + kind + ")");
        }
        log.info("Operator invitation email sent — to={}", RecipientMask.mask(toEmail));
    }

    String link(String token) {
        return UriComponentsBuilder.fromUriString(linkBaseUrl)
                .queryParam("token", token)
                .encode()
                .toUriString();
    }

    static String body(String link, String companyName, String inviterDisplayName, Instant expiresAt) {
        String who = inviterDisplayName == null || inviterDisplayName.isBlank()
                ? ""
                : inviterDisplayName.strip() + " 님이 ";
        String until = expiresAt == null ? "" : "\n링크는 " + EXPIRY.format(expiresAt) + " (UTC) 까지, 한 번만 쓸 수 있습니다.";
        return """
                안녕하세요.

                %s«%s» 의 운영자로 초대했습니다.

                아래 링크를 열고, 이 주소를 인증한 계정으로 로그인한 뒤 «수락» 을 누르면 운영자가 됩니다.
                %s
                %s
                초대를 기대하지 않았다면 이 메일을 무시하세요 — 수락하지 않으면 아무것도 바뀌지 않습니다.
                """.formatted(who, companyName, link, until);
    }
}
