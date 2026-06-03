package com.example.admin.infrastructure.persistence.rbac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-335 — the operator load-mutate-save adapter methods MUST
 * {@code saveAndFlush} (NOT {@code save}) so the managed-entity dirty UPDATE is
 * flushed WITHIN the request transaction. The request's main tx skips the
 * commit-time auto-flush in the demo runtime (readOnly flush-mode / OSIV), which
 * silently dropped status/password/profile UPDATEs while the endpoint still
 * returned 200 (a REQUIRES_NEW audit row + an IDENTITY-immediate create masked
 * it). An explicit flush executes even under FlushMode.MANUAL. This pins the
 * fix so a regression back to plain {@code save} fails here.
 */
@ExtendWith(MockitoExtension.class)
class JpaAdminOperatorAdapterFlushTest {

    @Mock
    AdminOperatorJpaRepository operatorRepository;
    @Mock
    AdminRoleJpaRepository roleRepository;
    @Mock
    AdminOperatorRoleJpaRepository operatorRoleRepository;

    @InjectMocks
    JpaAdminOperatorAdapter adapter;

    private AdminOperatorJpaEntity activeOperator() {
        return AdminOperatorJpaEntity.create(
                "op-uuid", "op@example.com", "hash", "Op", "ACTIVE", "acme-corp", Instant.now());
    }

    @Test
    void changeStatus_flushes_the_dirty_update() {
        AdminOperatorJpaEntity entity = activeOperator();
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(entity));

        adapter.changeStatus(7L, "SUSPENDED", Instant.now());

        assertThat(entity.getStatus()).isEqualTo("SUSPENDED");
        verify(operatorRepository).saveAndFlush(entity);
        verify(operatorRepository, never()).save(entity); // regression guard: NOT plain save
    }

    @Test
    void changePasswordHash_flushes_the_dirty_update() {
        AdminOperatorJpaEntity entity = activeOperator();
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(entity));

        adapter.changePasswordHash(7L, "new-hash", Instant.now());

        assertThat(entity.getPasswordHash()).isEqualTo("new-hash");
        verify(operatorRepository).saveAndFlush(entity);
        verify(operatorRepository, never()).save(entity);
    }

    @Test
    void changeFinanceDefaultAccountId_flushes_the_dirty_update() {
        AdminOperatorJpaEntity entity = activeOperator();
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(entity));

        adapter.changeFinanceDefaultAccountId(7L, "acct-123", Instant.now());

        assertThat(entity.getFinanceDefaultAccountId()).isEqualTo("acct-123");
        verify(operatorRepository).saveAndFlush(entity);
        verify(operatorRepository, never()).save(entity);
    }
}
