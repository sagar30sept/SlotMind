package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.Ids;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.SlotRepository;
import com.slotmind.persistence.SuggestionRepository;
import com.slotmind.scheduling.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SuggestionWriter {

    private static final Logger log = LoggerFactory.getLogger(SuggestionWriter.class);

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final SuggestionRepository suggestions;
    private final AppointmentViews views;

    public SuggestionWriter(
            AppointmentRepository appointments,
            SlotRepository slots,
            SuggestionRepository suggestions,
            AppointmentViews views) {
        this.appointments = appointments;
        this.slots = slots;
        this.suggestions = suggestions;
        this.views = views;
    }

    /**
     * Stores the first still-open recommendation and marks the appointment as waiting.
     * A pending suggestion for the same trigger is returned unchanged.
     */
    @Transactional
    public AppointmentResponse write(String appointmentId, TriggerReason trigger, List<Recommendation> recommendations) {
        Optional<RescheduleSuggestion> existing = suggestions
                .findFirstByAppointment_IdAndTriggerReasonAndStatus(appointmentId, trigger, SuggestionStatus.PENDING);
        if (existing.isPresent()) {
            log.debug("Appointment {} already has pending {} suggestion {}",
                    appointmentId, trigger, existing.get().getId());
            return views.toResponse(reload(appointmentId), existing.get());
        }
        Appointment appointment = reload(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            appointment.markReschedulePending();
        } else if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + appointmentId + " cannot be rescheduled from " + appointment.getStatus());
        }
        for (Recommendation recommendation : recommendations) {
            Optional<Slot> slot = slots.findWithProviderById(recommendation.slotId());
            if (slot.isEmpty() || slot.get().getStatus() != SlotStatus.OPEN) {
                log.debug("Skipping slot {} for appointment {}: slot is not open", recommendation.slotId(), appointmentId);
                continue;
            }
            if (slot.get().getProvider().getStatus() == ProviderStatus.OFFLINE) {
                log.debug("Skipping slot {} for appointment {}: provider is offline", recommendation.slotId(), appointmentId);
                continue;
            }
            if (recommendation.alternateProviderId() != null
                    && !recommendation.alternateProviderId().equals(slot.get().getProvider().getId())) {
                log.debug("Skipping slot {} for appointment {}: alternate provider does not match",
                        recommendation.slotId(), appointmentId);
                continue;
            }
            RescheduleSuggestion suggestion = suggestions.save(new RescheduleSuggestion(
                    Ids.next("SUG-"),
                    appointment,
                    slot.get(),
                    recommendation.alternateProviderId() == null ? null : slot.get().getProvider(),
                    recommendation.confidence(),
                    recommendation.reasoning(),
                    trigger));
            log.info("Stored suggestion {} for appointment {} on slot {} trigger {} confidence {}",
                    suggestion.getId(), appointmentId, slot.get().getId(), trigger, recommendation.confidence());
            return views.toResponse(appointment, suggestion);
        }
        log.info("No open slot matched appointment {} for trigger {}", appointmentId, trigger);
        throw new DomainException("NO_SLOT", "No open slot matches this appointment");
    }

    private Appointment reload(String appointmentId) {
        return appointments.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Appointment " + appointmentId + " was not found"));
    }
}
