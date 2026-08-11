package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.application.port.in.CheckArtistAccountUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal artist-account existence check — the remote counterpart of
 * community-service's port
 * {@code ArtistAccountChecker.isArtistAccount(String accountId, String tenantId)}
 * (TASK-FAN-BE-045 AC-6, ADR-004 ACCEPTED — A; contract:
 * {@code specs/contracts/http/artist-api.md} § Internal artist-account existence check).
 *
 * <p><strong>1:1 mapping.</strong> The two query parameters correspond exactly, in
 * name and meaning, to the two port parameters, and the response field
 * {@code exists} corresponds exactly to its boolean return value.
 *
 * <p>A domain "does not exist" is NOT an error — it returns 200 with
 * {@code exists=false}. An unknown account, an account in another tenant, and an
 * account that is simply not an artist are indistinguishable on purpose: the
 * caller needs a deny, not a reason.
 *
 * <p>Authentication is workload identity ({@code client_credentials} JWT) via the
 * Order(1) {@code /internal/**} chain (ADR-MONO-005). NOT gateway-routed.
 */
@RestController
@RequiredArgsConstructor
public class InternalArtistController {

    private final CheckArtistAccountUseCase checkArtistAccountUseCase;

    @GetMapping("/internal/artists/exists")
    public ExistsResponse exists(@RequestParam("accountId") String accountId,
                                 @RequestParam("tenantId") String tenantId) {
        return new ExistsResponse(checkArtistAccountUseCase.isArtistAccount(accountId, tenantId));
    }

    /** Response body of the existence check — {@code { "exists": <boolean> }}. */
    public record ExistsResponse(boolean exists) {}
}
