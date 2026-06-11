package com.example.security.access;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ADR-MONO-026 — the {@code SOURCE_IP} member of the closed access-condition
 * enum: gate an already-authorised action to requests whose source IP falls
 * within an allowed CIDR set.
 *
 * <p><b>Access conditions (the 2단계 of axis ②)</b> are a deliberately-bounded
 * AWS-IAM-{@code Condition} miniature: a fixed, code-defined set of condition
 * types ({@code TIME_WINDOW} / {@code SOURCE_IP} / {@code RESOURCE_TAG}), NOT a
 * policy engine. This class implements the {@code SOURCE_IP} type — the first
 * (iam admin) pilot (ADR-MONO-026 § D4). New condition types are added as
 * sibling classes in this package (a code change, never runtime registration —
 * the closed-enum boundary, § D1). See {@code platform/access-conditions.md}.
 *
 * <p><b>Carrier = domain/endpoint guard-config (ADR-MONO-026 § D3-B).</b> The
 * allowed-CIDR set is configured by the consuming domain (not carried on a JWT
 * claim — hence this lives under {@code com.example.security.access}, not
 * {@code …jwt}). This evaluator is framework-agnostic (raw strings only) so any
 * consumer can reuse it.
 *
 * <p><b>Semantics (the three invariants every access condition shares):</b>
 * <ul>
 *   <li><b>Restriction-only</b> — a condition can only GATE (deny when unmet) an
 *       action that already passed RBAC + tenant-scope + data-scope; it never
 *       grants. This class returns a boolean "satisfied"; the caller denies when
 *       it is {@code false} and the condition {@link #isConfigured() is
 *       configured}.</li>
 *   <li><b>Fail-safe</b> — an unresolvable / unparseable / blank source IP yields
 *       {@code false} (deny), never {@code true}. A configured allowlist whose
 *       entries are all invalid matches nothing → denies (a misconfiguration
 *       fails closed, it does not fall open).</li>
 *   <li><b>Net-zero / opt-in</b> — when no CIDR is configured ({@link
 *       #isConfigured()} is {@code false}) there is no gate; {@link
 *       #isSatisfiedBy(String)} returns {@code true} for every IP so an
 *       un-configured endpoint behaves exactly as before access-conditioning.</li>
 * </ul>
 *
 * <p>Supports IPv4 and IPv6 CIDRs (e.g. {@code 10.0.0.0/8}, {@code 2001:db8::/32})
 * and bare addresses (treated as {@code /32} / {@code /128}). IP literals are
 * parsed without DNS resolution: IPv4 is parsed manually (so a hostname like
 * {@code cafe.babe} never triggers a lookup) and IPv6 (which always contains a
 * {@code ':'} that no hostname may carry) via {@link InetAddress#getByName}.
 */
public final class SourceIpCondition {

    private final boolean configured;
    private final List<Cidr> allowed;

    private SourceIpCondition(boolean configured, List<Cidr> allowed) {
        this.configured = configured;
        this.allowed = allowed;
    }

    /**
     * Build a {@code SOURCE_IP} condition from a domain-configured allowlist.
     *
     * @param rawCidrs allowed CIDRs / IPs (e.g. {@code ["10.0.0.0/8", "203.0.113.5"]});
     *                 {@code null}, blank, and unparseable entries are dropped
     *                 (fail-safe — a dropped allowlist entry only narrows access).
     * @return a never-null condition. It is {@link #isConfigured() configured}
     *         iff at least one non-blank entry was supplied (even if every entry
     *         later proves unparseable — that is a misconfiguration that
     *         fail-closes, not net-zero).
     */
    public static SourceIpCondition fromAllowedCidrs(Collection<String> rawCidrs) {
        List<Cidr> parsed = new ArrayList<>();
        boolean anyDeclared = false;
        if (rawCidrs != null) {
            for (String raw : rawCidrs) {
                if (raw == null) {
                    continue;
                }
                String token = raw.trim();
                if (token.isEmpty()) {
                    continue;
                }
                anyDeclared = true;
                Cidr cidr = Cidr.parseOrNull(token);
                if (cidr != null) {
                    parsed.add(cidr);
                }
            }
        }
        return new SourceIpCondition(anyDeclared, List.copyOf(parsed));
    }

    /**
     * {@code true} iff the domain declared a non-empty allowlist — i.e. the
     * gate is active. When {@code false} the condition is net-zero (no gate);
     * callers MUST short-circuit on this before denying.
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Whether {@code sourceIp} satisfies the condition. {@code true} when the
     * condition is unconfigured (net-zero) or the IP is within an allowed CIDR;
     * {@code false} (fail-safe deny) when configured and the IP is blank,
     * unparseable, or outside every allowed CIDR.
     */
    public boolean isSatisfiedBy(String sourceIp) {
        if (!configured) {
            return true;
        }
        byte[] candidate = ipLiteralToBytes(sourceIp);
        if (candidate == null) {
            return false;
        }
        for (Cidr cidr : allowed) {
            if (cidr.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a textual IP literal to its address bytes (4 for IPv4, 16 for IPv6)
     * without DNS resolution, or {@code null} when not a valid literal.
     */
    private static byte[] ipLiteralToBytes(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.indexOf(':') >= 0) {
            // IPv6 — a ':' can never appear in a hostname, so getByName parses
            // the literal directly and never performs a DNS lookup.
            try {
                return InetAddress.getByName(s).getAddress();
            } catch (java.net.UnknownHostException ex) {
                return null;
            }
        }
        return ipv4LiteralToBytes(s);
    }

    /** Strict dotted-quad IPv4 parse (no DNS): exactly four 0-255 octets. */
    private static byte[] ipv4LiteralToBytes(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            int v = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                v = v * 10 + (c - '0');
            }
            if (v > 255) {
                return null;
            }
            out[i] = (byte) v;
        }
        return out;
    }

    /** A parsed CIDR block: network bytes + prefix length, same-family match. */
    private record Cidr(byte[] network, int prefixBits) {

        static Cidr parseOrNull(String token) {
            int slash = token.indexOf('/');
            if (slash < 0) {
                byte[] net = ipLiteralToBytes(token);
                return net == null ? null : new Cidr(net, net.length * 8);
            }
            byte[] net = ipLiteralToBytes(token.substring(0, slash));
            if (net == null) {
                return null;
            }
            int prefix;
            try {
                prefix = Integer.parseInt(token.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
            if (prefix < 0 || prefix > net.length * 8) {
                return null;
            }
            return new Cidr(net, prefix);
        }

        boolean contains(byte[] a) {
            if (a.length != network.length) {
                return false; // IPv4 vs IPv6 family mismatch
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (a[i] != network[i]) {
                    return false;
                }
            }
            int remBits = prefixBits % 8;
            if (remBits > 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                return (a[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }
    }
}
