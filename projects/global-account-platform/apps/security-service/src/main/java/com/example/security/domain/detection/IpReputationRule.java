package com.example.security.domain.detection;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IpReputationRule — fires when the login IP falls within a configured list
 * of suspicious CIDR ranges (e.g. known Tor exit nodes, VPN endpoints, open
 * proxies).
 *
 * <p>For portfolio purposes this uses a simple internal CIDR list configured
 * via {@code security.detection.ip-reputation.suspicious-cidrs} in
 * {@code application.yml}. In production this would be backed by an external
 * threat intelligence feed.</p>
 *
 * <p>Score is fixed at {@link DetectionThresholds#ipReputationScore()}
 * (default 60 → ALERT only). The default CIDR list is empty to avoid
 * false positives.</p>
 *
 * <p>Invalid CIDR entries are silently skipped (logged at construction
 * time). Masked IPs (e.g. {@code 1.2.3.***}) that cannot be parsed are
 * also skipped — the rule returns {@link DetectionResult#NONE}.</p>
 */
public class IpReputationRule implements SuspiciousActivityRule {

    public static final String CODE = "IP_REPUTATION";

    private final List<CidrRange> suspiciousRanges;
    private final DetectionThresholds thresholds;

    public IpReputationRule(List<String> suspiciousCidrs, DetectionThresholds thresholds) {
        this.thresholds = thresholds;
        if (suspiciousCidrs == null || suspiciousCidrs.isEmpty()) {
            this.suspiciousRanges = Collections.emptyList();
        } else {
            this.suspiciousRanges = suspiciousCidrs.stream()
                    .map(CidrRange::tryParse)
                    .filter(r -> r != null)
                    .toList();
        }
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        if (suspiciousRanges.isEmpty()) {
            return DetectionResult.NONE;
        }
        String ip = ctx.ipMasked();
        if (ip == null || ip.isBlank()) {
            return DetectionResult.NONE;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            // Masked or invalid IP — cannot evaluate
            return DetectionResult.NONE;
        }

        for (CidrRange range : suspiciousRanges) {
            if (range.contains(addr)) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("description", "Login IP matches suspicious CIDR range");
                evidence.put("matchedCidr", range.cidr());
                return new DetectionResult(CODE, thresholds.ipReputationScore(), evidence);
            }
        }
        return DetectionResult.NONE;
    }

    /**
     * Parsed CIDR range for efficient IP matching. Pure domain value.
     */
    static final class CidrRange {
        private final String cidr;
        private final byte[] network;
        private final int prefixLength;

        private CidrRange(String cidr, byte[] network, int prefixLength) {
            this.cidr = cidr;
            this.network = network;
            this.prefixLength = prefixLength;
        }

        String cidr() {
            return cidr;
        }

        boolean contains(InetAddress address) {
            byte[] addr = address.getAddress();
            if (addr.length != network.length) {
                return false; // IPv4 vs IPv6 mismatch
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < addr.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addr[fullBytes] & mask) != (network[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }

        static CidrRange tryParse(String cidr) {
            if (cidr == null || cidr.isBlank()) {
                return null;
            }
            try {
                String[] parts = cidr.split("/");
                InetAddress addr = InetAddress.getByName(parts[0].trim());
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : (addr.getAddress().length * 8);
                if (prefix < 0 || prefix > addr.getAddress().length * 8) {
                    return null;
                }
                return new CidrRange(cidr.trim(), addr.getAddress(), prefix);
            } catch (UnknownHostException | NumberFormatException e) {
                return null;
            }
        }
    }
}
