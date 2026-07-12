package com.example.finance.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The component scan must name {@link #LIB_PACKAGE} explicitly.
 *
 * <p>The shared edge filters ({@code RequestIdFilter}, {@code RetryAfterFilter}) and the shared
 * reactive {@code SecurityConfig} live in {@code libs/java-gateway}, outside this service's base
 * package. {@code @SpringBootApplication}'s default scan only covers
 * {@code com.example.finance.gateway}, so without the explicit list those beans would
 * <strong>never register</strong> — and nothing would say so. Every unit test would still pass,
 * because they construct the filters directly; the build would be green; the gateway would boot.
 * It would simply run <em>without its security chain</em>.
 *
 * <p>{@code GatewayComponentScanTest} asserts this list, so deleting the library package from it
 * fails the build rather than silently disarming the edge (TASK-MONO-357).
 */
@SpringBootApplication(scanBasePackages = {
        GatewayServiceApplication.OWN_PACKAGE,
        GatewayServiceApplication.LIB_PACKAGE
})
public class GatewayServiceApplication {

    static final String OWN_PACKAGE = "com.example.finance.gateway";
    static final String LIB_PACKAGE = "com.example.apigateway";

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
