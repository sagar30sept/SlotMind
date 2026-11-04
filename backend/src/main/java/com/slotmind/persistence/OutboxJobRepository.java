package com.slotmind.persistence;

import com.slotmind.domain.JobStatus;
import com.slotmind.domain.JobType;
import com.slotmind.domain.OutboxJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, String> {

    /** Returns ids of new or expired-lease jobs that are due to run. */
    @Query(value = """
            SELECT id FROM outbox_job
            WHERE attempts < max_attempts
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (status = 'NEW' OR (status = 'PROCESSING' AND locked_until < :now))
            ORDER BY created_at
            LIMIT :batch
            """, nativeQuery = true)
    List<String> findClaimableIds(@Param("now") Instant now, @Param("batch") int batch);

    /** Claims one job for this worker. Returns 1 when this worker won the row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE outbox_job
            SET status = 'PROCESSING',
                locked_by = :workerId,
                locked_until = :leaseUntil,
                attempts = attempts + 1,
                updated_at = :now
            WHERE id = :id
              AND attempts < max_attempts
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (status = 'NEW' OR (status = 'PROCESSING' AND locked_until < :now))
            """, nativeQuery = true)
    int claim(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    /** Marks a job this worker still holds as done. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE outbox_job
            SET status = 'DONE', locked_by = NULL, locked_until = NULL, updated_at = :now
            WHERE id = :id AND locked_by = :workerId AND status = 'PROCESSING'
            """, nativeQuery = true)
    int complete(@Param("id") String id, @Param("workerId") String workerId, @Param("now") Instant now);

    /** Returns a claimed job to new so a later poll can retry it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE outbox_job
            SET status = 'NEW',
                locked_by = NULL,
                locked_until = NULL,
                next_attempt_at = :nextAttemptAt,
                last_error = :lastError,
                updated_at = :now
            WHERE id = :id AND locked_by = :workerId AND status = 'PROCESSING'
            """, nativeQuery = true)
    int release(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    /** Marks a claimed job failed and stores the last error. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE outbox_job
            SET status = 'FAILED',
                locked_by = NULL,
                locked_until = NULL,
                last_error = :lastError,
                updated_at = :now
            WHERE id = :id AND locked_by = :workerId AND status = 'PROCESSING'
            """, nativeQuery = true)
    int fail(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    List<OutboxJob> findByJobTypeAndStatusOrderByUpdatedAtDesc(JobType jobType, JobStatus status);

    long countByJobType(JobType jobType);

    /** Returns whether a job with this dedupe key was already written. */
    boolean existsByDedupeKey(String dedupeKey);
}
