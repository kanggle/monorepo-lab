package com.example.admin.application;

import com.example.admin.application.exception.CurrentPasswordMismatchException;
import com.example.admin.application.exception.PasswordPolicyViolationException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeMyPasswordUseCaseTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock PasswordHasher passwordHasher;

    @InjectMocks ChangeMyPasswordUseCase useCase;

    @Test
    void changeMyPassword_valid_saves_new_hash() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.passwordHash())).thenReturn(true);
        when(passwordHasher.hash("NewPass2@")).thenReturn("new-hash-value");

        useCase.changeMyPassword("op-uuid", "OldPass1!", "NewPass2@");

        verify(passwordHasher, times(1)).verify("OldPass1!", op.passwordHash());
        verify(passwordHasher, times(1)).hash("NewPass2@");
        verify(operatorPort, times(1)).changePasswordHash(eq(10L), eq("new-hash-value"), any(Instant.class));
    }

    @Test
    void changeMyPassword_current_password_mismatch_throws() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("Wrong1!", op.passwordHash())).thenReturn(false);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "Wrong1!", "NewPass2@"))
                .isInstanceOf(CurrentPasswordMismatchException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorPort, never()).changePasswordHash(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void changeMyPassword_policy_violation_too_short_throws() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.passwordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", "Ab1!"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorPort, never()).changePasswordHash(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void changeMyPassword_policy_violation_two_categories_only_throws() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.passwordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", "alllower1"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorPort, never()).changePasswordHash(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void changeMyPassword_policy_violation_exceeds_128_chars_throws() {
        String tooLong = "A1!" + "a".repeat(126);
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(passwordHasher.verify("OldPass1!", op.passwordHash())).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.changeMyPassword("op-uuid", "OldPass1!", tooLong))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(operatorPort, never()).changePasswordHash(anyLong(), anyString(), any(Instant.class));
    }
}
