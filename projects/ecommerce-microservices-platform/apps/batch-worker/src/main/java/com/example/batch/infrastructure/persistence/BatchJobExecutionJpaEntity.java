package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "batch_job_execution_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BatchJobExecutionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BatchJobStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    BatchJobExecutionJpaEntity(Long id, String jobName, BatchJobStatus status,
                               Instant startedAt, Instant finishedAt, String errorMessage) {
        this.id = id;
        this.jobName = jobName;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
    }
}
