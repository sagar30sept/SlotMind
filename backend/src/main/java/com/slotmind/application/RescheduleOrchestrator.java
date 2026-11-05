package com.slotmind.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Ids;
import com.slotmind.domain.JobType;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.OutboxJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RescheduleOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RescheduleOrchestrator.class);

    private final OutboxJobRepository jobs;
    private final ObjectMapper objectMapper;

    public RescheduleOrchestrator(OutboxJobRepository jobs, ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    /**
     * Marks a booked (confirmed or already rescheduled) appointment as waiting
     * and enqueues one suggest job for this fan-out.
     */
    @Transactional
    public void enqueue(Appointment appointment, TriggerReason trigger, String fanoutJobId) {
        if (!AppointmentStatus.MOVABLE.contains(appointment.getStatus())) {
            log.debug("Skipped suggest for appointment {} in status {}", appointment.getId(), appointment.getStatus());
            return;
        }
        appointment.markReschedulePending();
        String dedupeKey = "SUGGEST:" + trigger + ":" + appointment.getId() + ":" + fanoutJobId;
        if (jobs.existsByDedupeKey(dedupeKey)) {
            log.debug("Suggest job already queued for appointment {} trigger {}", appointment.getId(), trigger);
            return;
        }
        jobs.save(new OutboxJob(
                Ids.next("JOB-"),
                JobType.SUGGEST,
                dedupeKey,
                payload(appointment.getId(), trigger, fanoutJobId)));
        log.info("Enqueued suggest job for appointment {} trigger {}", appointment.getId(), trigger);
    }

    /**
     * Enqueues a suggest job for a visit that is already waiting.
     * The appointment stays pending; this does not move a confirmed visit.
     */
    @Transactional
    public void enqueueWaiting(Appointment appointment, TriggerReason trigger, String sourceId) {
        if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
            log.debug("Skipped waiting suggest for appointment {} in status {}", appointment.getId(), appointment.getStatus());
            return;
        }
        String dedupeKey = "SUGGEST:" + trigger + ":" + appointment.getId() + ":" + sourceId;
        if (jobs.existsByDedupeKey(dedupeKey)) {
            log.debug("Suggest job already queued for waiting appointment {} trigger {}", appointment.getId(), trigger);
            return;
        }
        jobs.save(new OutboxJob(
                Ids.next("JOB-"),
                JobType.SUGGEST,
                dedupeKey,
                payload(appointment.getId(), trigger, sourceId)));
        log.info("Enqueued suggest job for waiting appointment {} trigger {}", appointment.getId(), trigger);
    }

    private String payload(String appointmentId, TriggerReason trigger, String fanoutJobId) {
        try {
            return objectMapper.writeValueAsString(new SuggestJob(appointmentId, trigger, fanoutJobId));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write suggest job", exception);
        }
    }

    public record SuggestJob(String appointmentId, TriggerReason triggerReason, String fanoutJobId) {
    }
}
