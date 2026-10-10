package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-MONO-772 S3 — body of {@code POST /internal/consumer-pool/signups}. The same constraints as the consumer
 * {@link SignupRequest} (auth-to-account.md: «검증은 소비자 가입과 같다»); no tenant field.
 *
 * <p>🔴 {@code toString} hides the password and masks the email — Spring's message converter logs the body via
 * {@code toString} at DEBUG (the 772 S2 CI lesson).
 */
public record ConsumerPoolSignupRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        String locale,

        String timezone
) {
    @Override
    public String toString() {
        return "ConsumerPoolSignupRequest[email=<masked>, password=<redacted>, displayName=" + displayName
                + ", locale=" + locale + ", timezone=" + timezone + "]";
    }
}
