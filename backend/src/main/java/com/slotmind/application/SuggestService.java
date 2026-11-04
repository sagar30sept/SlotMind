package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.SuggestionRepository;
import com.slotmind.scheduling.Recommendation;
import com.slotmind.scheduling.SchedulingContext;
import com.slotmind.scheduling.SpokenReason;
import com.slotmind.scheduling.StrategyRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SuggestService {

    private static final Logger log = LoggerFactory.getLogger(SuggestService.class);

    private final SchedulingContextFactory contexts;
    private final StrategyRegistry registry;
    private final SuggestionWriter writer;
    private final SuggestionRepository suggestions;
    private final AppointmentViews views;

    public SuggestService(
            SchedulingContextFactory contexts,
            StrategyRegistry registry,
            SuggestionWriter writer,
            SuggestionRepository suggestions,
            AppointmentViews views) {
        this.contexts = contexts;
        this.registry = registry;
        this.writer = writer;
        this.suggestions = suggestions;
        this.views = views;
    }

    /**
     * Returns the pending suggestion, or ranks open slots and stores the first usable one.
     * Slots the desk already rejected are skipped.
     */
    public AppointmentResponse suggest(String appointmentId) {
        Optional<AppointmentResponse> existing = pendingSuggestion(appointmentId);
        if (existing.isPresent()) {
            log.info("Returning the pending suggestion for appointment {}", appointmentId);
            return existing.get();
        }
        log.debug("Suggesting appointment {}", appointmentId);
        SchedulingContext context = requireSuggestable(appointmentId, TriggerReason.INITIAL);
        Set<String> rejected = new HashSet<>(suggestions.findRejectedSlotIds(appointmentId));
        SchedulingContext open = context.withoutSlots(rejected);
        List<Recommendation> recommendations = registry.recommend(open).stream()
                .filter(recommendation -> !rejected.contains(recommendation.slotId()))
                .toList();
        if (recommendations.isEmpty() && !rejected.isEmpty()) {
            log.info("Ranked slots for appointment {} were already rejected. Offering another open slot.", appointmentId);
            recommendations = anyRemainingSlot(open);
        }
        return writer.write(appointmentId, nextTrigger(context), recommendations);
    }

    /** Ranks open slots for an appointment that is confirmed or waiting to be rescheduled. */
    public List<Recommendation> recommend(String appointmentId, TriggerReason trigger) {
        return registry.recommend(requireSuggestable(appointmentId, trigger));
    }

    /** Loads scheduling context and rejects appointments that cannot be rescheduled. */
    public SchedulingContext requireSuggestable(String appointmentId, TriggerReason trigger) {
        SchedulingContext context = contexts.build(appointmentId, trigger);
        AppointmentStatus status = context.appointment().getStatus();
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + appointmentId + " cannot be rescheduled from " + status);
        }
        return context;
    }

    /** Returns the pending desk-requested suggestion when one is already stored. */
    public Optional<SuggestionResponse> pendingInitialSuggestion(String appointmentId) {
        return suggestions
                .findPending(appointmentId, TriggerReason.INITIAL, SuggestionStatus.PENDING)
                .map(views::toSuggestion);
    }

    /** Returns the appointment view that already carries a pending suggestion. */
    private Optional<AppointmentResponse> pendingSuggestion(String appointmentId) {
        return suggestions.findByAppointmentAndStatus(appointmentId, SuggestionStatus.PENDING).stream()
                .findFirst()
                .map(suggestion -> views.toResponse(suggestion.getAppointment(), suggestion));
    }

    /** Keeps the original trigger while the appointment is still waiting on the desk. */
    private TriggerReason nextTrigger(SchedulingContext context) {
        if (context.appointment().getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
            return TriggerReason.INITIAL;
        }
        return suggestions.findFirstByAppointment_IdOrderByCreatedAtDesc(context.appointment().getId())
                .map(RescheduleSuggestion::getTriggerReason)
                .orElse(TriggerReason.INITIAL);
    }

    /** Offers the earliest remaining open slots after every ranked slot was rejected. */
    private List<Recommendation> anyRemainingSlot(SchedulingContext context) {
        String currentProviderId = context.appointment().getProvider().getId();
        return context.availableSlots().stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .map(slot -> new Recommendation(
                        slot.getId(),
                        slot.getProvider().getId().equals(currentProviderId) ? null : slot.getProvider().getId(),
                        0.4,
                        SpokenReason.sentence(
                                context.appointment().getPatientName(),
                                slot.getStartTime(),
                                slot.getProvider().getName())))
                .toList();
    }
}
