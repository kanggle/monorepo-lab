package com.example.user.presentation.controller;

import com.example.user.application.command.UpdateProfileCommand;
import com.example.user.application.service.UserProfileService;
import com.example.user.presentation.dto.request.UpdateProfileRequest;
import com.example.user.presentation.dto.response.UpdateProfileResponse;
import com.example.user.presentation.dto.response.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        var result = userProfileService.getProfile(userId);
        return ResponseEntity.ok(UserProfileResponse.from(result));
    }

    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        var command = new UpdateProfileCommand(
                userId,
                request.nickname(),
                request.phone(),
                request.profileImageUrl()
        );
        var result = userProfileService.updateProfile(command);
        return ResponseEntity.ok(UpdateProfileResponse.from(result));
    }
}
