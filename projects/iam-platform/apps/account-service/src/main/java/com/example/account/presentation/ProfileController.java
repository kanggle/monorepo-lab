package com.example.account.presentation;

import com.example.account.application.command.UpdateProfileCommand;
import com.example.account.application.result.AccountMeResult;
import com.example.account.application.result.ProfileUpdateResult;
import com.example.account.application.service.ProfileUseCase;
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

    @GetMapping
    public ResponseEntity<AccountMeResponse> getMe(
            @RequestHeader("X-Account-Id") String accountId) {
        AccountMeResult result = profileUseCase.getMe(accountId);
        return ResponseEntity.ok(AccountMeResponse.from(result));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(
            @RequestHeader("X-Account-Id") String accountId,
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
        ProfileUpdateResult result = profileUseCase.updateProfile(command);
        return ResponseEntity.ok(ProfileUpdateResponse.from(result));
    }
}
