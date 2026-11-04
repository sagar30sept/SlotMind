package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.SuggestionAction;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.SuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionDecisionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionDecisionService.class);

    private final SuggestionRepository suggestions;
    private final AppointmentViews views;

    public SuggestionDecisionService(SuggestionRepository suggestions, AppointmentViews views) {
        this.suggestions = suggestions;
        this.views = views;
    }

    /**
     * Accepts or rejects a pending suggestion.
     * Accept moves the appointment onto the recommended slot in the same transaction.
     */
    @Transactional
    public AppointmentResponse decide(String suggestionId, SuggestionAction action) {
        RescheduleSuggestion suggestion = suggestions.findWithDetailsById(suggestionId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Suggestion " + suggestionId + " was not found"));
        if (action == SuggestionAction.REJECT) {
            suggestion.reject();
            log.info("Rejected suggestion {} for appointment {}", suggestionId, suggestion.getAppointment().getId());
            return views.toResponse(suggestion.getAppointment(), suggestion);
        }
        suggestion.accept();
        Appointment appointment = suggestion.getAppointment();
        if (appointment.getStatus() != com.slotmind.domain.AppointmentStatus.RESCHEDULE_PENDING) {
            throw new DomainException("APPOINTMENT_NOT_PENDING", "Appointment " + appointment.getId() + " is not waiting for the front desk");
        }
        Slot newSlot = suggestion.getRecommendedSlot();
        if (newSlot.getStatus() != com.slotmind.domain.SlotStatus.OPEN) {
            throw new DomainException("SLOT_NOT_OPEN", "That slot is no longer open");
        }
        if (newSlot.getProvider().isOffline()) {
            throw new DomainException("PROVIDER_OFFLINE", newSlot.getProvider().getName() + " is offline");
        }
        Slot oldSlot = appointment.getSlot();
        if (suggestion.getTriggerReason() != TriggerReason.SLOT_CANCELLED) {
            oldSlot.release();
        }
        newSlot.book();
        appointment.rescheduleTo(newSlot.getProvider(), newSlot);
        log.info("Accepted suggestion {} for appointment {} onto slot {} with {}",
                suggestionId, appointment.getId(), newSlot.getId(), newSlot.getProvider().getId());
        return views.toResponse(appointment, suggestion);
    }
}
