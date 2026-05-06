package com.example.scmplatform.inventoryvisibility.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock configuration — batch-heavy trait first code in scm-platform.
 * <p>
 * Uses JDBC lock provider (Postgres) so ShedLock state is durable across
 * restarts and visible in the database (no Redis dependency for locking).
 * <p>
 * The {@code shedlock} table is created by Flyway V1__init.sql.
 * Failure Scenario F in TASK-SCM-BE-003: prevents N replicas from all
 * running the staleness detection batch simultaneously.
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // use DB server time for lock expiry (avoids clock skew)
                        .build()
        );
    }
}
