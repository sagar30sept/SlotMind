package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.ProviderResponse;
import com.slotmind.api.SlotResponse;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.JobStatus;
import com.slotmind.domain.JobType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.SuggestionRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AppointmentViews {

    private final SuggestionRepository suggestions;
    private final OutboxJobRepository jobs;

    public AppointmentViews(SuggestionRepository suggestions, OutboxJobRepository jobs) {
        this.suggestions = suggestions;
        this.jobs = jobs;
    }

    /** Maps appointments to desk responses, attaching the latest suggestion for each. */
    public List<AppointmentResponse> list(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return List.of();
        }
        Map<String, RescheduleSuggestion> latest = new HashMap<>();
        List<String> ids = appointments.stream().map(Appointment::getId).toList();
        for (RescheduleSuggestion suggestion : suggestions.findByAppointmentIds(ids)) {
            latest.putIfAbsent(suggestion.getAppointment().getId(), suggestion);
        }
        return appointments.stream()
                .map(appointment -> toResponse(appointment, latest.get(appointment.getId())))
                .toList();
    }

    /** Builds the desk view, including the last failed suggest-job error when no suggestion exists. */
    public AppointmentResponse toResponse(Appointment appointment, RescheduleSuggestion suggestion) {
        String lastError = null;
        if (suggestion == null) {
            lastError = jobs.findByJobTypeAndStatusOrderByUpdatedAtDesc(JobType.SUGGEST, JobStatus.FAILED).stream()
                    .filter(job -> job.getPayload() != null && job.getPayload().contains(appointment.getId()))
                    .map(job -> job.getLastError())
                    .findFirst()
                    .orElse(null);
        }
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatientName(),
                appointment.getAppointmentType(),
                appointment.getProvider().getId(),
                appointment.getProvider().getName(),
                appointment.getSlot().getId(),
                appointment.getSlot().getStartTime(),
                appointment.getSlot().getDurationMinutes(),
                appointment.getStatus(),
                suggestion == null ? null : toSuggestion(suggestion),
                lastError);
    }

    /** Maps a stored suggestion to the fields the desk reads aloud and acts on. */
    public SuggestionResponse toSuggestion(RescheduleSuggestion suggestion) {
        Provider provider = suggestion.getRecommendedSlot().getProvider();
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getRecommendedSlot().getId(),
                suggestion.getRecommendedSlot().getStartTime(),
                provider.getName(),
                suggestion.getAlternateProvider() == null ? null : suggestion.getAlternateProvider().getId(),
                suggestion.getConfidence(),
                suggestion.getReasoning(),
                suggestion.getTriggerReason(),
                suggestion.getStatus());
    }

    public ProviderResponse toProvider(Provider provider) {
        return new ProviderResponse(provider.getId(), provider.getName(), provider.getSpecialty(), provider.getStatus());
    }

    public SlotResponse toSlot(Slot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getProvider().getId(),
                slot.getProvider().getName(),
                slot.getStartTime(),
                slot.getDurationMinutes(),
                slot.getStatus());
    }
}
