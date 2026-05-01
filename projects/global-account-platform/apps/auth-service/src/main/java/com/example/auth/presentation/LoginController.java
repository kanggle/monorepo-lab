package com.example.auth.presentation;

import com.example.auth.application.LoginUseCase;
import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.result.LoginResult;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.presentation.dto.LoginRequest;
import com.example.auth.presentation.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy login endpoint — email/password authentication producing a custom JWT pair.
 *
 * <p><b>DEPRECATED since 2026-05-01 (TASK-BE-251 ADR-001 D2-b).</b>
 * Clients should migrate to the standard OIDC {@code POST /oauth2/token} endpoint.
 * This endpoint will be removed on or after <b>2026-08-01</b> (90-day sunset period).
 *
 * <p>Every response includes:
 * <ul>
 *   <li>{@code Deprecation: true} (RFC 8594)</li>
 *   <li>{@code Sunset: Thu, 01 Aug 2026 00:00:00 GMT} (RFC 9745)</li>
 * </ul>
 *
 * @deprecated since 2026-05-01, for removal on 2026-08-01 — use {@code POST /oauth2/token} instead
 */
@Deprecated(since = "2026-05-01", forRemoval = true)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    /**
     * RFC 8594 Deprecation header value — signals deprecated API to API consumers.
     */
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String DEPRECATION_VALUE = "true";

    /**
     * RFC 9745 Sunset header value — removal date 2026-08-01.
     */
    private static final String SUNSET_HEADER = "Sunset";
    private static final String SUNSET_VALUE = "Sun, 01 Aug 2026 00:00:00 GMT";

    private final LoginUseCase loginUseCase;

    /**
     * Authenticates with email and password, returning a JWT access/refresh token pair.
     *
     * @deprecated since 2026-05-01 — migrate to {@code POST /oauth2/token
     *     (grant_type=authorization_code)} via the standard OIDC flow. See ADR-001.
     */
    @Deprecated(since = "2026-05-01", forRemoval = true)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // RFC 8594 + RFC 9745 deprecation signal headers
        httpResponse.setHeader(DEPRECATION_HEADER, DEPRECATION_VALUE);
        httpResponse.setHeader(SUNSET_HEADER, SUNSET_VALUE);

        SessionContext sessionContext = new SessionContext(
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("X-Device-Fingerprint"),
                httpRequest.getHeader("X-Geo-Country") != null
                        ? httpRequest.getHeader("X-Geo-Country") : "XX"
        );

        LoginCommand command = new LoginCommand(
                request.email(),
                request.password(),
                request.tenantId(),   // TASK-BE-229: pass optional tenant context
                sessionContext
        );

        LoginResult result = loginUseCase.execute(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }
}
