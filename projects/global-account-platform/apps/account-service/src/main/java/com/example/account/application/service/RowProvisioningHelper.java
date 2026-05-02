package com.example.account.application.service;

import com.example.account.application.command.BulkProvisionAccountCommand;
import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.result.ProvisionAccountResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * TASK-BE-257: Per-row transaction wrapper for bulk provisioning.
 *
 * <p>This bean is a separate Spring component so that the
 * {@code REQUIRES_NEW} propagation is applied via a proxy call from
 * {@link BulkProvisionAccountUseCase}. Calling a {@code REQUIRES_NEW} method
 * on {@code this} (same class) would bypass the proxy and collapse the
 * transactions, defeating the partial-success guarantee.
 *
 * <p>Each call to {@link #provisionRow} runs in its own transaction:
 * <ul>
 *   <li>On success — the account, profile, roles, outbox event, and audit row
 *       are committed atomically.
 *   <li>On failure — only that row's transaction is rolled back; previously
 *       committed rows are unaffected.
 * </ul>
 *
 * <p><b>Password handling</b>: The bulk API does not accept passwords from the caller.
 * A cryptographically-random temporary password is generated per account so that
 * {@link ProvisionAccountUseCase} can create the credential record without change.
 * The temporary password is not included in any response or log. Accounts created
 * via bulk provisioning should be sent a password-reset link through a separate
 * notification channel (follow-up task).
 */
@Component
@RequiredArgsConstructor
class RowProvisioningHelper {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProvisionAccountUseCase provisionAccountUseCase;

    /**
     * Provision a single account row in an independent transaction.
     *
     * @param tenantId   the target tenant (already validated by the caller)
     * @param item       the per-row provisioning data
     * @param operatorId the caller's operator identifier (request-level)
     * @return result of a successful provisioning
     * @throws com.example.account.application.exception.AccountAlreadyExistsException on email duplicate
     * @throws IllegalArgumentException on role name validation failure
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProvisionAccountResult provisionRow(String tenantId,
                                               BulkProvisionAccountCommand.Item item,
                                               String operatorId) {
        List<String> roles = item.roles() != null ? item.roles() : List.of();

        // Generate a cryptographically random temporary password.
        // The caller never sees this value; the account requires a password reset.
        String tempPassword = generateTempPassword();

        ProvisionAccountCommand command = new ProvisionAccountCommand(
                tenantId,
                item.email(),
                tempPassword,
                item.displayName(),
                null,       // locale: use tenant default
                null,       // timezone: use tenant default
                roles,
                operatorId
        );
        return provisionAccountUseCase.execute(command);
    }

    private static String generateTempPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        // URL-safe base64 gives ~32 chars, satisfying the min-8 constraint
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
