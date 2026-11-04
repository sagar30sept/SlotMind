package com.slotmind.agentic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.application.RescheduleOrchestrator;
import com.slotmind.application.SuggestService;
import com.slotmind.application.SuggestionWriter;
import com.slotmind.config.SlotMindProperties;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.OutboxJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxJobRepository jobs;
    private final AppointmentRepository appointments;
    private final RescheduleOrchestrator orchestrator;
    private final SuggestService suggestService;
    private final SuggestionWriter writer;
    private final SlotMindProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate claimTransaction;
    private final String workerId = UUID.randomUUID().toString();

    public OutboxWorker(
            OutboxJobRepository jobs,
            AppointmentRepository appointments,
            RescheduleOrchestrator orchestrator,
            SuggestService suggestService,
            SuggestionWriter writer,
            SlotMindProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.appointments = appointments;
        this.orchestrator = orchestrator;
        this.suggestService = suggestService;
        this.writer = writer;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.claimTransaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(
            fixedDelayString = "${slotmind.worker.poll-ms:1000}",
            initialDelayString = "${slotmind.worker.initial-delay-ms:1000}")
    /** Claims a batch of due jobs and runs each one this worker won. */
    public void poll() {
        Instant now = Instant.now();
        for (String id : jobs.findClaimableIds(now, properties.worker().batchSize())) {
            int claimed = claim(id, now);
            if (claimed == 1) {
                log.debug("Claimed job {}", id);
                process(id);
            } else {
                log.debug("Job {} was already claimed", id);
            }
        }
    }

    /** Runs the claimed job and records a failure when the handler throws. */
    public void process(String jobId) {
        OutboxJob job = jobs.findById(jobId).orElseThrow();
        try {
            switch (job.getJobType()) {
                case FANOUT_PROVIDER_OFFLINE -> fanoutProvider(job);
                case FANOUT_SLOT_CANCELLED -> fanoutSlot(job);
                case SUGGEST -> applySuggestion(job.getId());
            }
        } catch (RuntimeException exception) {
            log.error("Job {} failed", jobId, exception);
            giveUp(jobId, exception.getMessage());
        }
    }

    /** Takes a lease on the job when it is still claimable. Returns 1 if this worker won it. */
    private int claim(String id, Instant now) {
        return claimTransaction.execute(status -> jobs.claim(
                id,
                workerId,
                now.plusSeconds(properties.worker().leaseSeconds()),
                now));
    }

    /** Enqueues one suggest job for each confirmed appointment of the offline provider. */
    private void fanoutProvider(OutboxJob job) {
        transaction.executeWithoutResult(status -> {
            if (!owns(job.getId())) {
                return;
            }
            String providerId = read(jobs.findById(job.getId()).orElseThrow().getPayload()).path("providerId").asText();
            appointments.findByProviderAndStatus(providerId, AppointmentStatus.CONFIRMED)
                    .forEach(appointment -> orchestrator.enqueue(appointment, TriggerReason.PROVIDER_OFFLINE, job.getId()));
            jobs.complete(job.getId(), workerId, Instant.now());
        });
        log.info("Completed provider offline fan-out {}", job.getId());
    }

    /** Enqueues one suggest job for the confirmed appointment on the cancelled slot. */
    private void fanoutSlot(OutboxJob job) {
        transaction.executeWithoutResult(status -> {
            if (!owns(job.getId())) {
                return;
            }
            String slotId = read(jobs.findById(job.getId()).orElseThrow().getPayload()).path("slotId").asText();
            appointments.findBySlotAndStatus(slotId, AppointmentStatus.CONFIRMED)
                    .forEach(appointment -> orchestrator.enqueue(appointment, TriggerReason.SLOT_CANCELLED, job.getId()));
            jobs.complete(job.getId(), workerId, Instant.now());
        });
        log.info("Completed slot cancellation fan-out {}", job.getId());
    }

    /** Ranks the appointment, stores the recommendation, and completes the job. */
    public void applySuggestion(String jobId) {
        OutboxJob job = jobs.findById(jobId).orElseThrow();
        JsonNode payload = read(job.getPayload());
        String appointmentId = payload.path("appointmentId").asText();
        TriggerReason trigger = TriggerReason.valueOf(payload.path("triggerReason").asText());
        var recommendations = suggestService.recommend(appointmentId, trigger);
        try {
            transaction.executeWithoutResult(status -> {
                writer.write(appointmentId, trigger, recommendations);
                if (owns(jobId)) {
                    jobs.complete(jobId, workerId, Instant.now());
                }
            });
            log.info("Completed suggest job {} for appointment {}", jobId, appointmentId);
        } catch (DomainException exception) {
            log.info("Suggest job {} could not store a suggestion: {}", jobId, exception.getMessage());
            giveUp(jobId, exception.getMessage());
        }
    }

    public String workerId() {
        return workerId;
    }

    private boolean owns(String jobId) {
        return jobs.findById(jobId)
                .map(job -> workerId.equals(job.getLockedBy()))
                .orElse(false);
    }

    /** Releases the job for retry, or marks it failed once attempts are exhausted. */
    private void giveUp(String jobId, String error) {
        String message = error == null ? "Suggest job failed" : error;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        String lastError = message;
        claimTransaction.executeWithoutResult(status -> {
            OutboxJob job = jobs.findById(jobId).orElse(null);
            if (job == null || !workerId.equals(job.getLockedBy())) {
                return;
            }
            Instant now = Instant.now();
            if (job.getAttempts() >= job.getMaxAttempts()) {
                jobs.fail(jobId, workerId, lastError, now);
                log.error("Job {} marked failed after {} attempts: {}", jobId, job.getAttempts(), lastError);
            } else {
                jobs.release(jobId, workerId, now.plusSeconds(job.getAttempts() * 2L), lastError, now);
                log.info("Job {} released for retry: {}", jobId, lastError);
            }
        });
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read job payload", exception);
        }
    }
}
