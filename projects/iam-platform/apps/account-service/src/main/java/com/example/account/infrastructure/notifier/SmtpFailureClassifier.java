package com.example.account.infrastructure.notifier;

import com.example.account.application.exception.EmailDeliveryException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

/**
 * TASK-MONO-770 — sorts a Spring {@link MailException} into «retrying may help» and «retrying cannot help».
 *
 * <p>PERMANENT only on positive evidence that the <b>address</b> is the problem:
 * <ul>
 *   <li>{@link MailParseException} — the address could not even be parsed;</li>
 *   <li>an {@link AddressException} anywhere in a cause chain;</li>
 *   <li>a {@link SendFailedException} that names <i>invalid</i> addresses — the server rejected the recipient
 *       with a permanent (5xx) reply. A temporary (4xx) rejection lands in {@code getValidUnsentAddresses()}
 *       instead and stays TRANSIENT.</li>
 * </ul>
 * Everything else — connection refused, timeout, authentication, an exception this class has never seen — is
 * TRANSIENT. 🔴 «Cannot judge» is TRANSIENT on purpose: a wrong «permanent» tells the person to give up on a
 * mail that would have left on the next try (signup.md § 실패의 종류를 구별해 보고한다 — same principle).
 *
 * <p>auth-service's password-reset sender ({@code SmtpEmailSender}) deliberately has no copy: that endpoint
 * answers 204 whatever happens (it must not reveal whether the address exists), so there is no one to tell
 * the kind to.
 */
final class SmtpFailureClassifier {

    private SmtpFailureClassifier() {
    }

    static EmailDeliveryException.Kind classify(MailException e) {
        if (e instanceof MailParseException) {
            return EmailDeliveryException.Kind.PERMANENT;
        }
        if (e instanceof MailSendException send) {
            for (Exception each : send.getMessageExceptions()) {
                if (addressIsTheProblem(each)) {
                    return EmailDeliveryException.Kind.PERMANENT;
                }
            }
        }
        return addressIsTheProblem(e) ? EmailDeliveryException.Kind.PERMANENT : EmailDeliveryException.Kind.TRANSIENT;
    }

    private static boolean addressIsTheProblem(Throwable t) {
        int depth = 0;
        for (Throwable cur = t; cur != null && depth < 16; cur = cur.getCause(), depth++) {
            if (cur instanceof AddressException) {
                return true;
            }
            if (cur instanceof SendFailedException failed) {
                var invalid = failed.getInvalidAddresses();
                if (invalid != null && invalid.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
