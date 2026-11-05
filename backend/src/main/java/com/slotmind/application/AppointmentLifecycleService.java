package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.SuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Runs the front desk's day-of-visit actions: cancel, check in, complete, no-show, and reschedule. */
@Service
public class AppointmentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentLifecycleService.class);

    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;
    private final RescheduleOrchestrator orchestrator;
    private final AppointmentViews views;
    private final Clock clock;

    public AppointmentLifecycleService(
            AppointmentRepository appointments,
            SuggestionRepository suggestions,
            RescheduleOrchestrator orchestrator,
            AppointmentViews views,
            Clock clock) {
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.orchestrator = orchestrator;
        this.views = views;
        this.clock = clock;
    }

    /** Returns one appointment with its latest suggestion. */
    @Transactional(readOnly = true)
    public AppointmentResponse get(String id) {
        return views.list(List.of(load(id))).get(0);
    }

    /**
     * Cancels a visit, reopens its slot when the visit still holds it, and withdraws any pending proposal.
     */
    @Transactional
    public AppointmentResponse cancel(String id, String reason) {
        Appointment appointment = load(id);
        appointment.cancel(reason, clock.instant());
        // release() only reopens a BOOKED slot, so a slot the clinic cancelled stays cancelled.
        appointment.getSlot().release();
        for (RescheduleSuggestion pending : suggestions.findByAppointmentAndStatus(id, SuggestionStatus.PENDING)) {
            pending.reject();
        }
        log.info("Cancelled appointment {}; slot {} is now {}", id, appointment.getSlot().getId(),
                appointment.getSlot().getStatus());
        return views.toResponse(appointment, null);
    }

    /** Checks the patient in for their visit. */
    @Transactional
    public AppointmentResponse checkIn(String id) {
        Appointment appointment = load(id);
        appointment.checkIn(clock.instant());
        log.info("Checked in appointment {}", id);
        return views.toResponse(appointment, null);
    }

    /** Marks a checked-in visit as finished. */
    @Transactional
    public AppointmentResponse complete(String id) {
        Appointment appointment = load(id);
        appointment.markCompleted(clock.instant());
        log.info("Completed appointment {}", id);
        return views.toResponse(appointment, null);
    }

    /** Records that the patient missed the visit. The slot stays booked because its time has passed. */
    @Transactional
    public AppointmentResponse noShow(String id) {
        Appointment appointment = load(id);
        appointment.markNoShow(clock.instant());
        log.info("Marked appointment {} as a no-show", id);
        return views.toResponse(appointment, null);
    }

    /**
     * Puts a booked visit in the reschedule queue at the desk's request.
     * The outbox worker ranks a new slot; the desk accepts it like any other proposal.
     */
    @Transactional
    public AppointmentResponse requestReschedule(String id) {
        Appointment appointment = load(id);
        if (!AppointmentStatus.MOVABLE.contains(appointment.getStatus())) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + id + " cannot be rescheduled from " + appointment.getStatus());
        }
        // enqueue() moves the visit to RESCHEDULE_PENDING in this transaction.
        orchestrator.enqueue(appointment, TriggerReason.INITIAL, "DESK-v" + appointment.getVersion());
        log.info("Desk asked to reschedule appointment {}", id);
        return views.toResponse(appointment, null);
    }

    private Appointment load(String id) {
        return appointments.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Appointment " + id + " was not found"));
    }
}
