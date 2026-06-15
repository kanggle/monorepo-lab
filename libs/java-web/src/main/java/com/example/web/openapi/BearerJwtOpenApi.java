package com.example.web.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Shared builder for the JWT-Bearer OpenAPI document exposed by the platform's
 * servlet REST services' Swagger UI. Centralizes the global {@code bearerAuth}
 * HTTP-bearer (JWT) security scheme and the token-input hint so the scheme — and
 * its issuer description — is defined exactly once instead of being copy-pasted
 * into every service's {@code OpenApiConfig}.
 *
 * <p>This is a pure builder over {@code io.swagger.v3.oas.models} POJOs: it
 * references <em>no</em> Spring and <em>no</em> servlet API, so it is safe in the
 * framework-agnostic {@code libs/java-web} module (swagger-core models are
 * classpath-safe on both servlet and reactive stacks; the dependency is declared
 * {@code compileOnly} so it never leaks onto a consumer's runtime classpath —
 * see {@code build.gradle} and the MONO-044a servlet-leak background there).
 *
 * <p>Each service keeps its own {@code @Bean} producing the {@link OpenAPI} via
 * {@link #create}; only the title/description/version vary per service.
 *
 * <p>Documentation-only — has no bearing on runtime authentication or
 * authorization (those are enforced by the gateway + resource-server filters).
 */
public final class BearerJwtOpenApi {

    /** Security-scheme key referenced by Swagger UI's "Authorize" button. */
    public static final String BEARER_SCHEME = "bearerAuth";

    private static final String TOKEN_HINT =
        "IAM (GAP) OIDC 가 발급한 access token 을 입력하세요 (Bearer 접두어 제외, 토큰 값만).";

    private BearerJwtOpenApi() {
    }

    /**
     * Build an {@link OpenAPI} document exposing a single global {@code bearerAuth}
     * JWT security scheme applied to every operation.
     *
     * @param title       API title shown in Swagger UI
     * @param description API subtitle/description
     * @param version     API version (e.g. {@code "v1"})
     * @return a configured {@link OpenAPI} ready to expose as a Spring {@code @Bean}
     */
    public static OpenAPI create(String title, String description, String version) {
        return new OpenAPI()
            .info(new Info()
                .title(title)
                .description(description)
                .version(version))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description(TOKEN_HINT)));
    }
}
