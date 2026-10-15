package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.OperatorRowLockPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-MONO-773 S3 — {@link OperatorRowLockPort} as one native locking read. Native on purpose: the statement that
 * runs is exactly {@code SELECT id FROM admin_operators WHERE id = ? FOR UPDATE} (the contract's sentence —
 * onboarding-api.md § 신원당 상한), with no entity load into the persistence context. {@code MANDATORY}: a lock
 * outside a transaction would be released at once and protect nothing.
 */
@Component
public class JpaOperatorRowLockAdapter implements OperatorRowLockPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(long operatorInternalId) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT id FROM admin_operators WHERE id = :id FOR UPDATE")
                .setParameter("id", operatorInternalId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new OperatorNotFoundException("Operator not found for internal id " + operatorInternalId);
        }
    }
}
