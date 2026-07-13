package com.example.account.presentation;

import com.example.account.application.command.UpdateProfileCommand;
import com.example.account.application.result.AccountMeResult;
import com.example.account.application.result.ProfileUpdateResult;
import com.example.account.application.service.ProfileUseCase;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.request.UpdateProfileRequest;
import com.example.account.presentation.dto.response.AccountMeResponse;
import com.example.account.presentation.dto.response.ProfileUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts/me")
public class ProfileController {

    private final ProfileUseCase profileUseCase;

    /**
     * TASK-BE-507: {@code X-Tenant-Id} is the gateway-propagated tenant claim (BE-230).
     * Absent / blank / {@code "*"} resolves to {@code fan-platform} — byte-identical to
     * the pre-BE-507 hard-pin, so header-less callers are unaffected.
     */
    @GetMapping
    public ResponseEntity<AccountMeResponse> getMe(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        AccountMeResult result = profileUseCase.getMe(accountId, TenantId.fromHeaderOrDefault(tenantId));
        return ResponseEntity.ok(AccountMeResponse.from(result));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody UpdateProfileRequest request) {
        LocalDate birthDate = request.birthDate() != null ? LocalDate.parse(request.birthDate()) : null;
        UpdateProfileCommand command = new UpdateProfileCommand(
                accountId,
                request.displayName(),
                request.phoneNumber(),
                birthDate,
                request.locale(),
                request.timezone(),
                request.preferences()
        );
        ProfileUpdateResult result = profileUseCase.updateProfile(command, TenantId.fromHeaderOrDefault(tenantId));
        return ResponseEntity.ok(ProfileUpdateResponse.from(result));
    }
}
