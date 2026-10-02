package com.example.account.presentation.internal;

import com.example.account.application.service.ConsumerPoolLegacyMoveUseCase;
import com.example.account.presentation.dto.request.ConsumerPoolLegacyMoveRequest;
import com.example.account.presentation.dto.response.ConsumerPoolLegacyMoveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-618 (ADR-MONO-078 A; account-maintenance-internal.md) — the re-runnable maintenance run that moves
 * single-site consumer accounts into the consumer pool under the same id.
 *
 * <p>URL: {@code POST /internal/consumer-pool/legacy-moves}. Authentication: the {@code /internal/**} chain —
 * GAP {@code client_credentials} Bearer JWT with {@code internal.invoke} in real profiles (the dev/test bypass
 * authenticates tests), like {@link IdentityBackfillController}. The body is optional.
 */
@RestController
@RequiredArgsConstructor
public class ConsumerPoolLegacyMoveController {

    private final ConsumerPoolLegacyMoveUseCase legacyMoveUseCase;

    @PostMapping("/internal/consumer-pool/legacy-moves")
    public ResponseEntity<ConsumerPoolLegacyMoveResponse> move(
            @Valid @RequestBody(required = false) ConsumerPoolLegacyMoveRequest request) {
        Integer limit = request == null ? null : request.limit();
        String after = request == null ? null : request.afterAccountId();
        return ResponseEntity.ok(ConsumerPoolLegacyMoveResponse.from(legacyMoveUseCase.execute(limit, after)));
    }
}
