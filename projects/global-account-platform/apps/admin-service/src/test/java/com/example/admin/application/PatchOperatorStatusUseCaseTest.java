package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.SelfSuspendForbiddenException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatchOperatorStatusUseCaseTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AdminActionAuditor auditor;
    @Mock AdminRefreshTokenPort refreshTokenPort;

    @InjectMocks PatchOperatorStatusUseCase useCase;

    private OperatorContext actor() {
        return new OperatorContext("actor-uuid", "jti-1");
    }

    private AdminOperatorJpaEntity operator(Long id, String uuid, String email, String status) {
        AdminOperatorJpaEntity e = AdminOperatorJpaEntity.create(
                uuid, email, "hash", "Display", status, Instant.parse("2026-01-01T00:00:00Z"));
        setField(e, "id", id);
        return e;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    @DisplayName("ACTIVE → SUSPENDED 전환 시 모든 refresh token 을 강제 폐기한다")
    void patchStatus_active_to_suspended_revokes_refresh_tokens() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));
        when(auditor.newAuditId()).thenReturn("audit-sus");

        PatchOperatorStatusUseCase.PatchStatusResult result = useCase.patchStatus(
                "target-uuid", "SUSPENDED", actor(), "violation");

        assertThat(result.previousStatus()).isEqualTo("ACTIVE");
        assertThat(result.currentStatus()).isEqualTo("SUSPENDED");
        assertThat(target.getStatus()).isEqualTo("SUSPENDED");
        verify(refreshTokenPort).revokeAllForOperator(eq(77L), any(Instant.class), anyString());
    }

    @Test
    @DisplayName("SUSPENDED → ACTIVE 전환 시에는 refresh token 을 건드리지 않는다")
    void patchStatus_suspended_to_active_does_not_touch_tokens() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "SUSPENDED");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));
        when(auditor.newAuditId()).thenReturn("audit-rest");

        PatchOperatorStatusUseCase.PatchStatusResult result = useCase.patchStatus(
                "target-uuid", "ACTIVE", actor(), "cleared");

        assertThat(result.currentStatus()).isEqualTo("ACTIVE");
        verify(refreshTokenPort, never()).revokeAllForOperator(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("자기 자신을 SUSPENDED 로 전환하려 하면 거부한다")
    void patchStatus_self_suspend_rejected() {
        OperatorContext self = new OperatorContext("self-uuid", "jti-x");

        assertThatThrownBy(() -> useCase.patchStatus(
                "self-uuid", "SUSPENDED", self, "reason"))
                .isInstanceOf(SelfSuspendForbiddenException.class);

        verify(operatorRepository, never()).save(any());
    }

    @Test
    @DisplayName("현재 상태와 동일한 상태로 전환 요청 시 StateTransitionInvalidException 을 던진다")
    void patchStatus_same_status_throws_state_transition_invalid() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.patchStatus(
                "target-uuid", "ACTIVE", actor(), "reason"))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    @DisplayName("대상 운영자가 존재하지 않으면 OperatorNotFoundException 을 던진다")
    void patchStatus_missing_operator_throws_not_found() {
        when(operatorRepository.findByOperatorId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.patchStatus(
                "ghost", "SUSPENDED", actor(), "reason"))
                .isInstanceOf(OperatorNotFoundException.class);
    }
}
