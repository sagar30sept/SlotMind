package com.slotmind.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.api.SlotResponse;
import com.slotmind.domain.Ids;
import com.slotmind.domain.JobType;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotStatusService {

    private static final Logger log = LoggerFactory.getLogger(SlotStatusService.class);

    private final SlotRepository slots;
    private final OutboxJobRepository jobs;
    private final AppointmentViews views;
    private final ObjectMapper objectMapper;

    public SlotStatusService(
            SlotRepository slots,
            OutboxJobRepository jobs,
            AppointmentViews views,
            ObjectMapper objectMapper) {
        this.slots = slots;
        this.jobs = jobs;
        this.views = views;
        this.objectMapper = objectMapper;
    }

    /**
     * Changes a slot's status. Cancelling it enqueues one fan-out job for the appointment on that slot.
     */
    @Transactional
    public SlotResponse update(String slotId, SlotStatus status) {
        Slot slot = slots.findWithProviderById(slotId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Slot " + slotId + " was not found"));
        if (slot.getStatus() == status) {
            log.debug("Slot {} is already {}", slotId, status);
            return views.toSlot(slot);
        }
        SlotStatus previous = slot.getStatus();
        switch (status) {
            case OPEN -> slot.reopen();
            case BOOKED -> slot.book();
            case CANCELLED -> slot.cancel();
        }
        slots.saveAndFlush(slot);
        log.info("Slot {} status changed from {} to {}", slotId, previous, status);
        if (status == SlotStatus.CANCELLED) {
            String dedupeKey = "FANOUT:CANCEL:" + slot.getId() + ":v" + slot.getVersion();
            if (!jobs.existsByDedupeKey(dedupeKey)) {
                jobs.save(new OutboxJob(
                        Ids.next("JOB-"),
                        JobType.FANOUT_SLOT_CANCELLED,
                        dedupeKey,
                        json(slot.getId())));
                log.info("Enqueued cancellation fan-out for slot {}", slotId);
            } else {
                log.debug("Cancellation fan-out for slot {} already exists", slotId);
            }
        }
        return views.toSlot(slot);
    }

    private String json(String slotId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("slotId", slotId));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write fan-out job", exception);
        }
    }
}
