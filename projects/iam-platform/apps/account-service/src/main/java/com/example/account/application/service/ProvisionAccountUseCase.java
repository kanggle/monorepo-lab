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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final AccountIdentityProvisioner accountIdentityProvisioner;

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

            // 3b. TASK-BE-381/382 (ADR-036 M1/M2): born-unified — mint+assign the central identity
            // at creation; the resolved identityId is propagated to the credential row (M2).
            String identityId = mintAndAssignIdentity(tenantId, account);

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
            //    TASK-BE-313: pass tenantId so the credential row in auth_db
            //    inherits the same tenant scope as the account row. Without
            //    this, auth-service defaults to "fan-platform" and login fails
            //    the tenant scope check (TenantProvisioningE2ETest 401 surface).
            //    TASK-MONO-263 (ADR-032 D5 step 4): accountType is no longer carried —
            //    the account_type claim/column is gone (operators get domain roles at
            //    assume-tenant, BE-376).
            try {
                authServicePort.createCredential(
                        account.getId(), account.getEmail(), command.password(), command.tenantId(), identityId);
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

    /**
     * TASK-BE-381 (ADR-MONO-036 P1/P2, M1): mint the account's central identity at
     * creation (born-unified), fail-soft. The mint runs in a REQUIRES_NEW transaction
     * ({@link AccountIdentityProvisioner}) so a failure cannot poison this provisioning
     * transaction; on any failure the account is born unlinked (identity_id stays NULL,
     * reconciled later) — provisioning never blocks on the identity infrastructure
     * (the ADR-034 availability stance). The {@code reuseExisting} mint converges the
     * consumer and operator sides on the SAME identity (ADR-036 P1).
     *
     * @return the resolved central {@code identity_id}, or {@code null} when the mint
     *         failed (fail-soft) — the caller propagates it to the credential row (M2).
     */
    private String mintAndAssignIdentity(TenantId tenantId, Account account) {
        String identityId;
        try {
            identityId = accountIdentityProvisioner.mintIdentity(tenantId.value(), account.getEmail());
        } catch (RuntimeException e) {
            log.warn("born-unified identity mint failed (fail-soft, account {} born unlinked) tenant={}: {}",
                    account.getId(), tenantId.value(), e.toString());
            return null;
        }
        if (identityId != null) {
            accountRepository.assignIdentityId(tenantId, account.getId(), identityId);
        }
        return identityId;
    }
}
