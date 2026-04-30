package com.example.auth.presentation;

import com.example.auth.application.GetCurrentSessionUseCase;
import com.example.auth.application.ListSessionsUseCase;
import com.example.auth.application.RevokeAllOtherSessionsUseCase;
import com.example.auth.application.RevokeSessionUseCase;
import com.example.auth.application.result.DeviceSessionResult;
import com.example.auth.application.result.ListSessionsResult;
import com.example.auth.application.result.RevokeOthersResult;
import com.example.auth.presentation.dto.DeviceSessionResponse;
import com.example.auth.presentation.dto.ListSessionsResponse;
import com.example.auth.presentation.dto.RevokeOthersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account device-session HTTP surface.
 *
 * <p>Spec: specs/contracts/http/auth-api.md ("GET /api/accounts/me/sessions",
 * "GET /api/accounts/me/sessions/current", "DELETE /api/accounts/me/sessions/{deviceId}",
 * "DELETE /api/accounts/me/sessions").
 *
 * <p>Authentication is gateway-enforced: the gateway validates the access token and
 * forwards {@code X-Account-Id} (= {@code sub}) and {@code X-Device-Id} (= {@code device_id}
 * claim) headers. This service does not re-validate JWTs on these endpoints.
 */
@RestController
@RequestMapping("/api/accounts/me/sessions")
@RequiredArgsConstructor
public class AccountSessionController {

    private final ListSessionsUseCase listSessionsUseCase;
    private final GetCurrentSessionUseCase getCurrentSessionUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;
    private final RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;

    @GetMapping
    public ResponseEntity<ListSessionsResponse> list(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
        ListSessionsResult result = listSessionsUseCase.execute(accountId, currentDeviceId);
        return ResponseEntity.ok(ListSessionsResponse.from(result));
    }

    @GetMapping("/current")
    public ResponseEntity<DeviceSessionResponse> current(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
        DeviceSessionResult result = getCurrentSessionUseCase.execute(accountId, currentDeviceId);
        return ResponseEntity.ok(DeviceSessionResponse.from(result));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> revoke(
            @RequestHeader("X-Account-Id") String accountId,
            @PathVariable("deviceId") String deviceId) {
        revokeSessionUseCase.execute(accountId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<RevokeOthersResponse> revokeOthers(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
        RevokeOthersResult result =
                revokeAllOtherSessionsUseCase.execute(accountId, currentDeviceId);
        return ResponseEntity.ok(new RevokeOthersResponse(result.revokedCount()));
    }
}
