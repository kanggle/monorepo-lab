package com.example.account.presentation;

import com.example.account.application.command.SignupCommand;
import com.example.account.application.result.SignupResult;
import com.example.account.application.service.SignupUseCase;
import com.example.account.presentation.dto.request.SignupRequest;
import com.example.account.presentation.dto.response.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class SignupController {

    private final SignupUseCase signupUseCase;

    /**
     * TASK-BE-507: {@code X-Tenant-Id} is the tenant of the OIDC client the user registered
     * through — auth-service resolves it from the saved {@code /oauth2/authorize} request
     * ({@code SavedRequestTenantResolver}) and forwards it here. Absent / blank / {@code "*"}
     * → {@code fan-platform}, byte-identical to the pre-BE-507 hard-pin, so a header-less
     * caller (and every existing fan consumer) is unaffected.
     */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody SignupRequest request) {
        SignupCommand command = new SignupCommand(
                request.email(),
                request.password(),
                request.displayName(),
                request.locale(),
                request.timezone(),
                tenantId
        );
        SignupResult result = signupUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(result));
    }
}
