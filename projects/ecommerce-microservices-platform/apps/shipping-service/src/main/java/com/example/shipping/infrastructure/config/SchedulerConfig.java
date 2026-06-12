package com.example.shipping.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock wiring for shipping-service (TASK-BE-360). The unattended auto-collect tracking
 * scheduler ({@code AutoCollectTrackingScheduler}) runs single-instance across replicas: only
 * one node acquires the {@code shipping-auto-collect-tracking} lock per tick (AC-2). Uses the
 * JDBC lock provider over the service datasource (DB-visible, durable lock state); the
 * {@code shedlock} table is created by Flyway V6.
 *
 * <p>{@code @EnableSchedulerLock} is unconditional and harmless when the scheduler bean is
 * absent (auto-collect disabled, AC-3) — no scheduled method is wrapped, so no lock is ever
 * taken. {@code @EnableScheduling} stays on the application class (it already drives the
 * outbox poller / processed-event cleanup).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
