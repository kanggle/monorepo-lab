package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.port.in.CheckArtistAccountUseCase;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves {@link CheckArtistAccountUseCase} — the artist-side half of
 * community-service's follow-target validation (TASK-FAN-BE-045 AC-6, ADR-004 A).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistAccountQueryService implements CheckArtistAccountUseCase {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isArtistAccount(String accountId, String tenantId) {
        if (accountId == null || accountId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        try {
            return artistRepository.existsByTenantIdAndAccountId(tenantId, accountId);
        } catch (RuntimeException e) {
            // Fail-closed on infrastructure failure. The caller cannot tell this
            // apart from a genuine "no such artist account", which is the point:
            // an error must not read as permission.
            log.warn("artist-account existence check failed (account={} tenant={}) "
                            + "→ fail-closed deny: {}",
                    accountId, tenantId, e.toString());
            return false;
        }
    }
}
