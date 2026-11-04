package com.slotmind.ai;

import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingContext;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PromptFactory {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a").withZone(ZoneId.systemDefault());

    /** Builds the ranking prompt, including a spoken-reasoning instruction and the open slots. */
    public String forContext(SchedulingContext context) {
        TriggerReason trigger = context.trigger();
        String situation = trigger == TriggerReason.PROVIDER_OFFLINE
                ? offlineInstruction(context)
                : sameProviderInstruction(context);
        return """
                You are a clinic scheduling assistant. Reply with one JSON object and nothing else.
                Fields: slotId (string), alternateProviderId (string or null), confidence (number from 0 to 1), reasoning (string).
                slotId and alternateProviderId are machine fields. reasoning is spoken aloud to the patient by front-desk staff.
                reasoning must use the patient name, a clock time, and the doctor's name. Do not mention ids, statuses, or confidence.

                %s

                Appointment:
                - patient: %s
                - urgency: %s
                - current provider: %s (%s, id %s)
                - current slot: %s for %d minutes (id %s)
                - trigger: %s

                Available slots:
                %s

                Provider roster:
                %s
                """.formatted(
                situation,
                context.appointment().getPatientName(),
                context.appointment().getAppointmentType(),
                context.appointment().getProvider().getName(),
                context.appointment().getProvider().getSpecialty(),
                context.appointment().getProvider().getId(),
                CLOCK.format(context.appointment().getSlot().getStartTime()),
                context.appointment().getSlot().getDurationMinutes(),
                context.appointment().getSlot().getId(),
                trigger,
                slots(context),
                roster(context));
    }

    private String offlineInstruction(SchedulingContext context) {
        return "The current provider is offline and cannot be chosen. Pick another provider's open slot and set alternateProviderId to that provider's id.";
    }

    private String sameProviderInstruction(SchedulingContext context) {
        return "Prefer the same provider's nearest open slot and leave alternateProviderId null. Choose a different provider only when this provider has no open slot.";
    }

    private String slots(SchedulingContext context) {
        if (context.availableSlots().isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (Slot slot : context.availableSlots()) {
            builder.append("- id ").append(slot.getId())
                    .append(", start ").append(CLOCK.format(slot.getStartTime()))
                    .append(", duration ").append(slot.getDurationMinutes())
                    .append(" minutes, provider ").append(slot.getProvider().getName())
                    .append(" (").append(slot.getProvider().getId())
                    .append(", ").append(slot.getProvider().getSpecialty())
                    .append(")\n");
        }
        return builder.toString().trim();
    }

    private String roster(SchedulingContext context) {
        StringBuilder builder = new StringBuilder();
        for (Provider provider : context.roster()) {
            builder.append("- id ").append(provider.getId())
                    .append(", ").append(provider.getName())
                    .append(", ").append(provider.getSpecialty())
                    .append(", status ").append(provider.getStatus())
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
