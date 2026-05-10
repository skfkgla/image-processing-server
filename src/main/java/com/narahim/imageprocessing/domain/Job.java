package com.narahim.imageprocessing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "jobs",
    indexes = @Index(name = "idx_jobs_status", columnList = "status")
)
@Getter
@NoArgsConstructor
public class Job {

    @Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	@Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "worker_job_id")
    private String workerJobId;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Job create(String idempotencyKey, String imageUrl) {
        Job job = new Job();
        job.idempotencyKey = idempotencyKey;
        job.imageUrl = imageUrl;
        job.status = JobStatus.PENDING;
        job.createdAt = LocalDateTime.now();
        job.updatedAt = LocalDateTime.now();
        return job;
    }

    public void startProcessing(String workerJobId) {
        if (this.status != JobStatus.PENDING) {
            throw new IllegalStateException("Can only transition to PROCESSING from PENDING, current: " + this.status);
        }
        this.workerJobId = workerJobId;
        this.status = JobStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete(String result) {
        if (this.status != JobStatus.PROCESSING) {
            throw new IllegalStateException("Can only transition to COMPLETED from PROCESSING, current: " + this.status);
        }
        this.result = result;
        this.status = JobStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        if (this.status != JobStatus.PENDING && this.status != JobStatus.PROCESSING) {
            throw new IllegalStateException("Can only transition to FAILED from PENDING or PROCESSING, current: " + this.status);
        }
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage, String result) {
        fail(errorMessage);
        this.result = result;
    }
}
