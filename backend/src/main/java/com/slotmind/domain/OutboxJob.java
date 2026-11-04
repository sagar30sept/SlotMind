package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_job", indexes = {
        @Index(name = "idx_outbox_claim", columnList = "status, next_attempt_at, created_at")
})
public class OutboxJob {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private JobType jobType;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 160)
    private String dedupeKey;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutboxJob() {
    }

    public OutboxJob(String id, JobType jobType, String dedupeKey, String payload) {
        Instant now = Instant.now();
        this.id = id;
        this.jobType = jobType;
        this.dedupeKey = dedupeKey;
        this.payload = payload;
        this.status = JobStatus.NEW;
        this.attempts = 0;
        this.maxAttempts = 5;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public JobType getJobType() {
        return jobType;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
