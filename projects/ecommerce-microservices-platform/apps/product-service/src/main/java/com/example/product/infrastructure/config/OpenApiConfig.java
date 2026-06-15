package com.example.product.infrastructure.config;

import com.example.web.openapi.BearerJwtOpenApi;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI 문서에 JWT Bearer 인증 스키마를 노출한다.
 * 공용 스키마/토큰 힌트 정의는 {@link BearerJwtOpenApi}. (문서화 전용 — 런타임 enforcement 무관)
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return BearerJwtOpenApi.create(
            "Product Service API",
            "ecommerce-microservices-platform · 상품 / 셀러 서비스",
            "v1");
    }
}
