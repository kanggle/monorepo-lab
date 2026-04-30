package com.example.admin.application;

import com.example.admin.application.exception.CurrentPasswordMismatchException;
import com.example.admin.application.exception.PasswordPolicyViolationException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.gap.security.password.PasswordHasher;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeMyPasswordUseCaseTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock PasswordHasher passwordHasher;

    @InjectMocks ChangeMyPasswordUseCase useCase;

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
    void changeMyPassword_valid_saves_new_hash() {
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        String originalHash = op.getPasswordHash();
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", originalHash)).thenReturn(true);
        when(passwordHasher.hash("NewPass2@")).thenReturn("new-hash-value");

        useCase.changeMyPassword("op-uuid", "OldPass1!", "NewPass2@");

        assertThat(op.getPasswordHash()).isEqualTo("new-hash-value");
        verify(passwordHasher, times(1)).verify("OldPass1!", originalHash);
        verify(passwordHasher, times(1)).hash("NewPass2@");
        verify(operatorRepository, times(1)).save(op);
    }

    @Test
    void changeMyPassword_current_password_mismatch_throws() {
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("Wrong1!", op.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "Wrong1!", "NewPass2@"))
                .isInstanceOf(CurrentPasswordMismatchException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorRepository, never()).save(any(AdminOperatorJpaEntity.class));
    }

    @Test
    void changeMyPassword_policy_violation_too_short_throws() {
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", "Ab1!"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorRepository, never()).save(any(AdminOperatorJpaEntity.class));
    }

    @Test
    void changeMyPassword_policy_violation_two_categories_only_throws() {
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", "alllower1"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorRepository, never()).save(any(AdminOperatorJpaEntity.class));
    }

    @Test
    void changeMyPassword_policy_violation_exceeds_128_chars_throws() {
        String tooLong = "A1!" + "a".repeat(126);
        AdminOperatorJpaEntity op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", tooLong))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorRepository, never()).save(any(AdminOperatorJpaEntity.class));
    }
}
