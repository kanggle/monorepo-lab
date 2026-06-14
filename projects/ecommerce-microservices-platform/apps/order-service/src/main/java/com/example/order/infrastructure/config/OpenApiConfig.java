package com.example.order.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI / OpenAPI 문서에 JWT Bearer 인증 스키마를 노출한다.
 * "Authorize" 버튼으로 access token 을 입력하면 보호된 엔드포인트 호출에
 * Authorization: Bearer &lt;token&gt; 헤더가 자동 첨부된다. (문서화 전용 — 런타임 enforcement 무관)
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Order Service API")
                .description("ecommerce-microservices-platform · 주문 서비스")
                .version("v1"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("auth-service 가 발급한 access token 을 입력하세요 (Bearer 접두어 제외, 토큰 값만).")));
    }
}
