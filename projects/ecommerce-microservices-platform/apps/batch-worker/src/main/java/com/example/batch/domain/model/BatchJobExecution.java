package com.example.batch.domain.model;

import java.time.Instant;

public class BatchJobExecution {

    private final Long id;
    private final String jobName;
    private BatchJobStatus status;
    private final Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    private BatchJobExecution(Long id, String jobName, BatchJobStatus status,
                               Instant startedAt, Instant finishedAt, String errorMessage) {
        this.id = id;
        this.jobName = jobName;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
    }

    public static BatchJobExecution start(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName must not be null or blank");
        }
        return new BatchJobExecution(null, jobName, BatchJobStatus.RUNNING, Instant.now(), null, null);
    }

    public static BatchJobExecution reconstitute(Long id, String jobName, BatchJobStatus status,
                                                   Instant startedAt, Instant finishedAt,
                                                   String errorMessage) {
        return new BatchJobExecution(id, jobName, status, startedAt, finishedAt, errorMessage);
    }

    public void complete() {
        this.status = BatchJobStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage must not be null or blank");
        }
        this.status = BatchJobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public BatchJobStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
