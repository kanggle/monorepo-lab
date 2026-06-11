package com.example.security.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceIpCondition — ADR-MONO-026 SOURCE_IP access condition (closed-enum, fail-safe, opt-in)")
class SourceIpConditionTest {

    @Test
    @DisplayName("net-zero: no CIDR configured ⟹ not configured, every IP satisfies (no gate)")
    void unconfiguredIsNetZero() {
        SourceIpCondition none = SourceIpCondition.fromAllowedCidrs(null);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.isSatisfiedBy("203.0.113.5")).isTrue();

        SourceIpCondition empty = SourceIpCondition.fromAllowedCidrs(List.of());
        assertThat(empty.isConfigured()).isFalse();
        assertThat(empty.isSatisfiedBy("8.8.8.8")).isTrue();

        SourceIpCondition blanks = SourceIpCondition.fromAllowedCidrs(Arrays.asList("  ", null, ""));
        assertThat(blanks.isConfigured()).isFalse();
        assertThat(blanks.isSatisfiedBy("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("IPv4 CIDR: in-range IP satisfies, out-of-range denied")
    void ipv4CidrMatch() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("10.0.0.0/8"));
        assertThat(c.isConfigured()).isTrue();
        assertThat(c.isSatisfiedBy("10.1.2.3")).isTrue();
        assertThat(c.isSatisfiedBy("10.255.255.254")).isTrue();
        assertThat(c.isSatisfiedBy("11.0.0.1")).isFalse();
        assertThat(c.isSatisfiedBy("192.168.0.1")).isFalse();
    }

    @Test
    @DisplayName("non-byte-aligned prefix (/12) masks the partial byte correctly")
    void nonByteAlignedPrefix() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("172.16.0.0/12"));
        assertThat(c.isSatisfiedBy("172.16.0.1")).isTrue();
        assertThat(c.isSatisfiedBy("172.31.255.255")).isTrue();
        assertThat(c.isSatisfiedBy("172.32.0.1")).isFalse(); // just outside /12
        assertThat(c.isSatisfiedBy("172.15.255.255")).isFalse();
    }

    @Test
    @DisplayName("bare IP is treated as /32 (exact match only)")
    void bareIpIsExact() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("203.0.113.5"));
        assertThat(c.isSatisfiedBy("203.0.113.5")).isTrue();
        assertThat(c.isSatisfiedBy("203.0.113.6")).isFalse();
    }

    @Test
    @DisplayName("multiple CIDRs: satisfied iff within ANY (OR within one condition, not a combinator)")
    void multipleCidrsAreUnioned() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("10.0.0.0/8", "203.0.113.0/24"));
        assertThat(c.isSatisfiedBy("10.9.9.9")).isTrue();
        assertThat(c.isSatisfiedBy("203.0.113.200")).isTrue();
        assertThat(c.isSatisfiedBy("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("IPv6 CIDR matches; IPv4 candidate against IPv6 CIDR is a family mismatch (denied)")
    void ipv6CidrAndFamilyMismatch() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("2001:db8::/32"));
        assertThat(c.isSatisfiedBy("2001:db8:abcd::1")).isTrue();
        assertThat(c.isSatisfiedBy("2001:dead::1")).isFalse();
        assertThat(c.isSatisfiedBy("10.0.0.1")).isFalse(); // family mismatch, not a crash
    }

    @Test
    @DisplayName("fail-safe: configured + blank/unparseable IP ⟹ denied")
    void failSafeOnBadIp() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("10.0.0.0/8"));
        assertThat(c.isSatisfiedBy(null)).isFalse();
        assertThat(c.isSatisfiedBy("")).isFalse();
        assertThat(c.isSatisfiedBy("   ")).isFalse();
        assertThat(c.isSatisfiedBy("not-an-ip")).isFalse();
        assertThat(c.isSatisfiedBy("10.0.0.999")).isFalse(); // invalid octet
    }

    @Test
    @DisplayName("fail-closed: configured but ALL entries unparseable ⟹ configured, matches nothing (deny)")
    void failClosedOnAllInvalidEntries() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("garbage", "10.0.0.0/99"));
        assertThat(c.isConfigured()).isTrue();              // operator declared an allowlist
        assertThat(c.isSatisfiedBy("10.0.0.1")).isFalse();  // but nothing matches → deny, not open
    }

    @Test
    @DisplayName("partially-valid allowlist: invalid entries dropped, valid ones still gate")
    void partiallyValidAllowlist() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("garbage", "10.0.0.0/8"));
        assertThat(c.isConfigured()).isTrue();
        assertThat(c.isSatisfiedBy("10.1.1.1")).isTrue();
        assertThat(c.isSatisfiedBy("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("entries are trimmed before parsing")
    void entriesAreTrimmed() {
        SourceIpCondition c = SourceIpCondition.fromAllowedCidrs(List.of("  10.0.0.0/8  "));
        assertThat(c.isSatisfiedBy(" 10.2.2.2 ")).isTrue();
    }
}
