package com.example.auth.presentation;

import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.presentation.dto.CreateCredentialRequest;
import com.example.auth.presentation.dto.CreateCredentialResponse;
import com.example.auth.presentation.dto.ForceLogoutRequest;
import com.example.auth.presentation.dto.ForceLogoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for credential provisioning.
 *
 * <p>TASK-BE-063: account-service calls this after persisting a new Account so
 * auth_db.credentials has a row to authenticate against. Must never be exposed
 * through the public gateway (S2). See
 * {@code specs/contracts/http/internal/auth-internal.md}.</p>
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalCredentialController {

    private final CreateCredentialUseCase createCredentialUseCase;
    private final ForceLogoutUseCase forceLogoutUseCase;

    @PostMapping("/credentials")
    public ResponseEntity<CreateCredentialResponse> createCredential(
            @Valid @RequestBody CreateCredentialRequest request) {
        CreateCredentialResult result = createCredentialUseCase.execute(
                new CreateCredentialCommand(
                        request.accountId(),
                        request.email(),
                        request.password(),
                        request.tenantId()  // TASK-BE-229: pass optional tenant context
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateCredentialResponse.from(result));
    }

    @PostMapping("/accounts/{accountId}/force-logout")
    public ResponseEntity<ForceLogoutResponse> forceLogout(
            @PathVariable String accountId,
            @RequestBody(required = false) ForceLogoutRequest body) {
        ForceLogoutUseCase.Result result = forceLogoutUseCase.execute(accountId);
        return ResponseEntity.ok(ForceLogoutResponse.from(result));
    }
}
