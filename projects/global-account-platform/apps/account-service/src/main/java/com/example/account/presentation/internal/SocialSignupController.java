package com.example.account.presentation.internal;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.application.service.SocialSignupUseCase;
import com.example.account.presentation.dto.request.SocialSignupRequest;
import com.example.account.presentation.dto.response.SocialSignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class SocialSignupController {

    private final SocialSignupUseCase socialSignupUseCase;

    @PostMapping("/social-signup")
    public ResponseEntity<SocialSignupResponse> socialSignup(
            @Valid @RequestBody SocialSignupRequest request) {
        SocialSignupCommand command = new SocialSignupCommand(
                request.email(),
                request.provider(),
                request.providerUserId(),
                request.displayName()
        );

        SocialSignupResult result = socialSignupUseCase.execute(command);
        SocialSignupResponse response = SocialSignupResponse.from(result);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
