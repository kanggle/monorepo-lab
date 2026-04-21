package com.example.auth.presentation.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpResolver 단위 테스트")
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("X-Forwarded-For가 없으면 remoteAddr을 반환한다")
    void resolve_noXff_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("X-Forwarded-For에 단일 IP가 있으면 해당 IP를 반환한다")
    void resolve_singleXff_returnsFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("X-Forwarded-For에 여러 IP가 있으면 첫 번째 IP를 반환한다")
    void resolve_multipleXff_returnsFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("X-Forwarded-For가 빈 문자열이면 remoteAddr을 반환한다")
    void resolve_emptyXff_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("X-Forwarded-For가 공백 문자열이면 remoteAddr을 반환한다")
    void resolve_blankXff_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("X-Forwarded-For에 IPv6 주소가 포함되어도 첫 번째 IP를 반환한다")
    void resolve_ipv6Xff_returnsFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "2001:db8::1, 203.0.113.50");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("X-Forwarded-For IP 앞뒤 공백이 제거된다")
    void resolve_xffWithSpaces_trimmed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "  203.0.113.50  ,  70.41.3.18  ");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }
}
