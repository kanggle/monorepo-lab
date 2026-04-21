package com.example.observability.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceFilterTest {

    private final MdcTraceFilter filter = new MdcTraceFilter();

    @Test
    @DisplayName("필터 체인 실행 중 MDC에 traceId가 설정된다")
    void traceId_setInMdc_duringFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedTraceId.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(capturedTraceId.get()).isNotNull();
    }

    @Test
    @DisplayName("필터 체인 완료 후 MDC에서 traceId가 제거된다")
    void traceId_removedFromMdc_afterFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("OpenTelemetry Span이 없으면 traceId는 빈 문자열이다")
    void traceId_emptyString_whenNoSpan() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedTraceId.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(capturedTraceId.get()).isEmpty();
    }
}
