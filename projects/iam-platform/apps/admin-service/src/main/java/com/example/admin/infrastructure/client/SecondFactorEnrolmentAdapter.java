package com.example.admin.infrastructure.client;

import com.example.admin.application.port.SecondFactorEnrolmentPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TASK-MONO-771 S5 — {@link SecondFactorEnrolmentPort}: the tenant roster from admin_db, the enrolment answer from
 * auth-service in chunks of the provider's per-call cap (admin-to-auth.md § second-factor enrolment status).
 */
@Component
@RequiredArgsConstructor
public class SecondFactorEnrolmentAdapter implements SecondFactorEnrolmentPort {

    /** The provider's cap (auth-service {@code SecondFactorEnrolmentStatusQuery.MAX_ACCOUNT_IDS}). */
    static final int CHUNK = 500;

    private final AdminOperatorJpaRepository operatorRepository;
    private final AuthServiceClient authServiceClient;

    @Override
    @Transactional(readOnly = true)
    public List<String> activeOperatorAccountIdsOf(String tenantId) {
        return operatorRepository.findActiveOidcSubjectsInTenantScope(tenantId);
    }

    @Override
    public Set<String> enrolledAmong(Collection<String> accountIds) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(accountIds));
        Set<String> enrolled = new LinkedHashSet<>();
        for (int from = 0; from < distinct.size(); from += CHUNK) {
            enrolled.addAll(authServiceClient.enrolledAmong(
                    distinct.subList(from, Math.min(from + CHUNK, distinct.size()))));
        }
        return enrolled;
    }
}
