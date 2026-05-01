package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.error.GatewayErrorHandler;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class AccountTypeValidationFilterTest {

    private final GatewayErrorHandler errorHandler = new GatewayErrorHandler(new ObjectMapper());
    private final AccountTypeValidationFilter filter = new AccountTypeValidationFilter(errorHandler);

    @Test
    void allowsOperatorAccountType() {
        Jwt jwt = buildJwt("OPERATOR");
        CapturingChain chain = new CapturingChain();

        runFilter(jwt, chain);

        assertThat(chain.called).isTrue();
    }

    @Test
    void rejectsConsumerAccountTypeWith403() {
        Jwt jwt = buildJwt("CONSUMER");
        CapturingChain chain = new CapturingChain();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMissingAccountTypeClaimWith403() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of("iss", "test", "sub", "user-x")))
                .build();
        CapturingChain chain = new CapturingChain();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void passesWhenNoSecurityContext() {
        CapturingChain chain = new CapturingChain();

        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // No security context wired — simulates public/actuator path
        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
    }

    private void runFilter(Jwt jwt, CapturingChain chain) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();
    }

    private static Jwt buildJwt(String accountType) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.put("sub", "user-42");
                    c.put("account_type", accountType);
                })
                .build();
    }

    private static final class CapturingChain implements GatewayFilterChain {
        boolean called = false;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return Mono.empty();
        }
    }
}
