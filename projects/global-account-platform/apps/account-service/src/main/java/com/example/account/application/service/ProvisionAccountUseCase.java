package com.example.account.application.service;

import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-231: Creates a new account under a specific enterprise tenant.
 *
 * <p>Persists Account + Profile + role assignments in a single transaction,
 * then publishes the outbox {@code account.created} event with {@code tenant_id}.
 *
 * <p>Audit: records {@code OPERATOR_PROVISIONING_CREATE} in account_status_history.
 */
@Service
@RequiredArgsConstructor
public class ProvisionAccountUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountEventPublisher eventPublisher;
    private final AuthServicePort authServicePort;

    @Transactional
    public ProvisionAccountResult execute(ProvisionAccountCommand command) {
        TenantId tenantId = new TenantId(command.tenantId());

        // 1. Validate tenant exists and is ACTIVE
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        if (!tenant.isActive()) {
            throw new TenantSuspendedException(command.tenantId());
        }

        // 2. Check email uniqueness within this tenant
        String normalizedEmail = command.email().trim().toLowerCase();
        if (accountRepository.existsByEmail(tenantId, normalizedEmail)) {
            throw new AccountAlreadyExistsException(command.email());
        }

        try {
            // 3. Create Account
            Account account = Account.create(tenantId, normalizedEmail);
            account = accountRepository.save(account);

            // 4. Create Profile
            Profile profile = Profile.create(
                    account.getId(),
                    command.displayName(),
                    command.locale(),
                    command.timezone()
            );
            profileRepository.save(profile, tenantId);

            // 5. Persist role assignments
            //    TASK-BE-255: AccountRole.create now requires a grantedBy attribution.
            //    We use the provisioning operatorId (or, when null, fall back to the
            //    tenantId itself so the row is never granted-by-NULL during creation).
            List<String> roles = command.roles() != null ? command.roles() : List.of();
            String grantedBy = command.operatorId() != null ? command.operatorId() : command.tenantId();
            for (String roleName : roles) {
                AccountRole role = AccountRole.create(tenantId, account.getId(), roleName, grantedBy);
                accountRoleRepository.save(role);
            }

            // 6. Persist credential in auth-service
            try {
                authServicePort.createCredential(account.getId(), account.getEmail(), command.password());
            } catch (AuthServicePort.CredentialAlreadyExistsConflict e) {
                throw new AccountAlreadyExistsException(command.email());
            }

            // 7. Audit: record provisioning creation in account_status_history
            String operatorId = command.operatorId() != null ? command.operatorId() : command.tenantId();
            AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                    command.tenantId(),
                    account.getId(),
                    AccountStatus.ACTIVE,   // from = ACTIVE (initial state)
                    AccountStatus.ACTIVE,   // to   = ACTIVE (no transition, this is creation)
                    StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE,
                    "provisioning_system",
                    operatorId,
                    "{\"action\":\"OPERATOR_PROVISIONING_CREATE\",\"tenantId\":\"" + command.tenantId() + "\"}"
            );
            historyRepository.save(auditEntry);

            // 8. Publish outbox account.created event (tenant_id included in payload)
            eventPublisher.publishAccountCreated(account, account.getTenantId().value(), profile.getLocale());

            return ProvisionAccountResult.from(account, roles);

        } catch (DataIntegrityViolationException e) {
            throw new AccountAlreadyExistsException(command.email());
        }
    }
}
