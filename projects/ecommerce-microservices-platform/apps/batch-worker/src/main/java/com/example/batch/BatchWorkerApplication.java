package com.example.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * batch-worker application entry point.
 *
 * <p>{@code @EnableScheduling} was removed by TASK-BE-064 (no real jobs existed at that time).
 * TASK-BE-409 re-enables it as part of the shared scheduling scaffolding for
 * {@code searchIndexConsistencyCheckJob} and future jobs (BE-410/BE-411). ShedLock is wired
 * via {@link com.example.batch.infrastructure.config.SchedulerConfig} to guarantee single-instance
 * execution across replicas ({@code platform/service-types/batch-job.md} — "분산락 필수").
 */
@SpringBootApplication
@EnableScheduling
public class BatchWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchWorkerApplication.class, args);
    }
}
