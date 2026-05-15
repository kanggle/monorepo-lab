package com.example.admin.application;

import com.example.admin.application.exception.CurrentPasswordMismatchException;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.PasswordPolicyViolationException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChangeMyPasswordUseCase {

    private final AdminOperatorPort operatorPort;
    private final PasswordHasher passwordHasher;

    @Transactional
    public void changeMyPassword(String operatorUuid, String currentPassword, String newPassword) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found for operatorId=" + operatorUuid));

        if (!passwordHasher.verify(currentPassword, operator.passwordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        validatePasswordPolicy(newPassword);

        String newHash = passwordHasher.hash(newPassword);
        operatorPort.changePasswordHash(operator.internalId(), newHash, Instant.now());
    }

    private static void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new PasswordPolicyViolationException(
                    "Password must be 8–128 characters long");
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) categories++;
        if (password.chars().anyMatch(Character::isLowerCase)) categories++;
        if (password.chars().anyMatch(Character::isDigit)) categories++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) categories++;
        if (categories < 3) {
            throw new PasswordPolicyViolationException(
                    "Password must contain at least 3 of: uppercase, lowercase, digit, special character");
        }
    }
}
