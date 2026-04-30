package com.example.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingFilter 단위 테스트")
class LoggingFilterUnitTest {

    @Mock
    private GatewayFilterChain chain;

    private LoggingFilter filter;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        filter = new LoggingFilter();

        // Capture log output
        logger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    @DisplayName("필터 완료 시 latency 포함 로그가 기록된다")
    void filter_completesAndLogsLatency() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header("X-Request-ID", "test-request-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(logAppender.list).isNotEmpty();
        String logMessage = logAppender.list.get(0).getFormattedMessage();
        assertThat(logMessage).contains("HTTP", "GET", "/api/accounts/me");
        assertThat(logMessage).containsPattern("\\d+ms");
    }

    @Test
    @DisplayName("MDC에 request-id, method, path가 설정된다")
    void filter_setsMdcContext() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Request-ID", "mdc-test-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify MDC was populated during logging (check via log event MDC map)
        assertThat(logAppender.list).isNotEmpty();
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry("request-id", "mdc-test-id");
        assertThat(event.getMDCPropertyMap()).containsEntry("method", "POST");
        assertThat(event.getMDCPropertyMap()).containsEntry("path", "/api/auth/login");
    }

    @Test
    @DisplayName("Authorization 헤더 값이 로그에 포함되지 않는다 (보안 규칙)")
    void filter_doesNotLogAuthorizationHeaderValue() {
        String secretToken = "Bearer eyJhbGciOiJSUzI1NiJ9.secret-payload.signature";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header("Authorization", secretToken)
                .header("X-Request-ID", "auth-test-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(logAppender.list).isNotEmpty();
        for (ILoggingEvent event : logAppender.list) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(secretToken);
            assertThat(message).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
            assertThat(message).doesNotContain("secret-payload");
        }
    }

    @Test
    @DisplayName("X-Request-ID 헤더가 없어도 에러 없이 완료된다")
    void filter_noRequestId_completesWithoutError() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(logAppender.list).isNotEmpty();
    }
}
