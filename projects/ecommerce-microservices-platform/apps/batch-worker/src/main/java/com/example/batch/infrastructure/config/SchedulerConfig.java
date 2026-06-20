package com.example.batch.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock wiring for batch-worker (TASK-BE-409). All batch jobs run single-instance across
 * replicas: only one node acquires the named ShedLock row per tick
 * ({@code platform/service-types/batch-job.md} — "주어진 잡은 동시 1 인스턴스만 실행 — 분산락 필수").
 * Uses the JDBC lock provider over the service datasource (DB-visible, durable lock state); the
 * {@code shedlock} table is created by Flyway V2.
 *
 * <p>{@code @EnableSchedulerLock} wraps every {@code @Scheduled}+{@code @SchedulerLock} method;
 * scheduled methods without the lock annotation are unaffected. {@code @EnableScheduling} lives
 * on {@link com.example.batch.BatchWorkerApplication} (re-added by TASK-BE-409).
 *
 * <p>Mirrors shipping-service {@code SchedulerConfig} (TASK-BE-360) for monorepo consistency.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
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
