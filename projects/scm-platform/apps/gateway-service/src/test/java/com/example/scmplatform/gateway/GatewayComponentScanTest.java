package com.example.scmplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Guards the worst failure mode of the library extraction (TASK-MONO-351).
 *
 * <p>{@code RequestIdFilter}, {@code RetryAfterFilter} and the reactive
 * {@code SecurityConfig} now live in {@code libs/java-gateway}, i.e. outside this
 * service's base package. Their {@code @Component} / {@code @Configuration} annotations
 * are therefore <em>not enough</em>: unless {@code com.example.apigateway} appears in
 * this application's component scan, Spring never sees them.
 *
 * <p>What makes that dangerous is how quiet it is. Every unit test constructs the filters
 * directly, so they all still pass. The build is green. The gateway boots. It simply runs
 * <strong>without its security chain and without its edge filters</strong> — and nothing
 * anywhere says so.
 *
 * <p>This test makes that regression loud: remove the library package from
 * {@code @SpringBootApplication(scanBasePackages = ...)} and the build fails here.
 */
@DisplayName("GatewayServiceApplication — 컴포넌트 스캔이 공유 라이브러리 패키지를 포함하는가")
class GatewayComponentScanTest {

    @Test
    void componentScanIncludesTheSharedGatewayLibraryPackage() {
        SpringBootApplication annotation =
                GatewayServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.scanBasePackages())
                .as("libs/java-gateway beans (RequestIdFilter, RetryAfterFilter, SecurityConfig) "
                        + "live outside com.example.scmplatform.gateway and register only if scanned explicitly")
                .contains("com.example.apigateway")
                .contains("com.example.scmplatform.gateway");
    }
}
