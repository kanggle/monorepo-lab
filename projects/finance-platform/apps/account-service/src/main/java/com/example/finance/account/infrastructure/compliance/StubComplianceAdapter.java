package com.example.finance.account.infrastructure.compliance;

import com.example.common.id.UuidV7;
import com.example.finance.account.application.port.outbound.CompliancePort;
import com.example.finance.account.domain.compliance.ScreeningDecision;
import com.example.finance.account.domain.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * v1 in-process AML/sanction screening (architecture.md § KYC/AML Compliance
 * Gate). Deterministic: an owner ref (or counterparty owner ref) present in
 * the configured sanction list returns {@code SANCTION_HIT}; everything else
 * is {@code CLEAR}. v2 swaps a real provider behind {@link CompliancePort}
 * (no domain change).
 *
 * <p>Configurable for tests via
 * {@code financeplatform.account.compliance.sanctioned-owner-refs} (CSV) —
 * the IT suite seeds a known sanctioned ref to assert the F4 path.
 *
 * <p>F7: never logs the raw owner ref — only a masked prefix + a non-PII
 * screening ref is emitted/returned.
 */
@Slf4j
@Component
public class StubComplianceAdapter implements CompliancePort {

    private final Set<String> sanctionedOwnerRefs;

    public StubComplianceAdapter(
            @Value("${financeplatform.account.compliance.sanctioned-owner-refs:}")
            String csv) {
        this.sanctionedOwnerRefs = new HashSet<>();
        if (csv != null && !csv.isBlank()) {
            Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(sanctionedOwnerRefs::add);
        }
    }

    @Override
    public ScreeningDecision screen(String ownerRef, String counterpartyOwnerRef, Money amount) {
        String screeningRef = "scr-" + UuidV7.randomString();
        boolean hit = sanctionedOwnerRefs.contains(ownerRef)
                || (counterpartyOwnerRef != null
                && sanctionedOwnerRefs.contains(counterpartyOwnerRef));
        if (hit) {
            log.warn("Sanction screen HIT screeningRef={} ownerRefMask={}",
                    screeningRef, mask(ownerRef));
            return ScreeningDecision.sanctionHit(screeningRef);
        }
        return ScreeningDecision.clear(screeningRef);
    }

    /** Mask all but the first 4 chars (F7 — no plaintext PII in logs). */
    private static String mask(String v) {
        if (v == null || v.length() <= 4) return "****";
        return v.substring(0, 4) + "****";
    }
}
