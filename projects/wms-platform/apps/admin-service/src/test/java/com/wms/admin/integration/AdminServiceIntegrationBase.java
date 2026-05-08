package com.wms.admin.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for admin-service integration tests. Boots a Postgres container and
 * publishes its JDBC URL as Spring properties so {@code @SpringBootTest}
 * picks it up before Flyway runs.
 *
 * <p>Mirrors the master-service / notification-service patterns. Tagged
 * {@code @Tag("integration")} so the default {@code test} task skips them
 * and they only run via {@code integrationTest}.
 */
public abstract class AdminServiceIntegrationBase {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("admin_db")
                    .withUsername("admin")
                    .withPassword("admin")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.flyway.enabled=true"
            ).applyTo(applicationContext.getEnvironment());
        }
    }
}
