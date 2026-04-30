package com.example.auth.domain.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpMaskerTest {

    @Test
    @DisplayName("IPv4 masks last two octets")
    void ipv4TwoOctetMask() {
        assertThat(IpMasker.mask("192.168.1.42")).isEqualTo("192.168.*.*");
        assertThat(IpMasker.mask("10.0.0.50")).isEqualTo("10.0.*.*");
        assertThat(IpMasker.mask("127.0.0.1")).isEqualTo("127.0.*.*");
    }

    @Test
    @DisplayName("IPv6 keeps top 48 bits and appends ::*")
    void ipv6Mask() {
        assertThat(IpMasker.mask("2001:db8:85a3:1:2:3:4:5"))
                .isEqualTo("2001:db8:85a3::*");
    }

    @Test
    @DisplayName("null/blank/garbage returns 'unknown'")
    void unknownInputs() {
        assertThat(IpMasker.mask(null)).isEqualTo("unknown");
        assertThat(IpMasker.mask("")).isEqualTo("unknown");
        assertThat(IpMasker.mask("   ")).isEqualTo("unknown");
    }
}
