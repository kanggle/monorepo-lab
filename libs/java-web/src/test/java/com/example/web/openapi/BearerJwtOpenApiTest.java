package com.example.web.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class BearerJwtOpenApiTest {

    @Test
    void create_passesInfoThrough() {
        OpenAPI api = BearerJwtOpenApi.create("Order Service API", "subtitle", "v1");

        assertThat(api.getInfo().getTitle()).isEqualTo("Order Service API");
        assertThat(api.getInfo().getDescription()).isEqualTo("subtitle");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
    }

    @Test
    void create_buildsGlobalBearerJwtScheme() {
        OpenAPI api = BearerJwtOpenApi.create("t", "d", "v1");

        assertThat(api.getSecurity()).hasSize(1);
        assertThat(api.getSecurity().get(0)).containsKey(BearerJwtOpenApi.BEARER_SCHEME);

        SecurityScheme scheme =
            api.getComponents().getSecuritySchemes().get(BearerJwtOpenApi.BEARER_SCHEME);
        assertThat(scheme.getName()).isEqualTo(BearerJwtOpenApi.BEARER_SCHEME);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(scheme.getDescription()).contains("access token");
    }
}
