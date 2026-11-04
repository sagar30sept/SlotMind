package com.slotmind.scheduling;

import com.slotmind.ai.PromptFactory;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.TriggerReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleAndPromptTest {

    private final PromptFactory prompts = new PromptFactory();
    private final RuleBasedStrategy rules = new RuleBasedStrategy();

    @Test
    void rulePrefersSameProviderThenMatchingSpecialty() {
        Provider sarah = new Provider("PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.OFFLINE);
        Provider rahul = new Provider("PRV-004", "Dr. Rahul Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        Provider james = new Provider("PRV-002", "Dr. James Okonkwo", Specialty.SPECIALIST, ProviderStatus.AVAILABLE);
        Slot current = new Slot("SLT-001", sarah, Instant.now().plus(1, ChronoUnit.HOURS), 30, SlotStatus.BOOKED);
        Slot laterGeneral = new Slot("SLT-005", rahul, Instant.now().plus(150, ChronoUnit.MINUTES), 30, SlotStatus.OPEN);
        Slot soonerGeneral = new Slot("SLT-007", rahul, Instant.now().plus(90, ChronoUnit.MINUTES), 30, SlotStatus.OPEN);
        Slot specialist = new Slot("SLT-004", james, Instant.now().plus(30, ChronoUnit.MINUTES), 30, SlotStatus.OPEN);
        Appointment appointment = new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, sarah, current);

        List<Recommendation> ordered = rules.recommend(new SchedulingContext(
                appointment,
                TriggerReason.PROVIDER_OFFLINE,
                List.of(laterGeneral, specialist, soonerGeneral),
                List.of(sarah, rahul, james)));

        assertEquals(List.of("SLT-007", "SLT-005"), ordered.stream().map(Recommendation::slotId).toList());
        assertTrue(ordered.get(0).reasoning().contains("John Martinez"));
        assertTrue(ordered.get(0).reasoning().contains("Dr. Rahul Verma"));
        assertFalse(ordered.get(0).reasoning().contains("SLT-"));
        assertEquals("PRV-004", ordered.get(0).alternateProviderId());
    }

    @Test
    void offlineAndCancelledPromptsDiffer() {
        Provider sarahOffline = new Provider("PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.OFFLINE);
        Provider sarahBack = new Provider("PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.AVAILABLE);
        Slot current = new Slot("SLT-001", sarahOffline, Instant.now().plus(1, ChronoUnit.HOURS), 30, SlotStatus.BOOKED);
        Slot currentBack = new Slot("SLT-001", sarahBack, Instant.now().plus(1, ChronoUnit.HOURS), 30, SlotStatus.BOOKED);
        Appointment offlineVisit = new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, sarahOffline, current);
        Appointment returnedVisit = new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, sarahBack, currentBack);
        SchedulingContext offline = new SchedulingContext(offlineVisit, TriggerReason.PROVIDER_OFFLINE, List.of(), List.of(sarahOffline));
        SchedulingContext returned = new SchedulingContext(returnedVisit, TriggerReason.PROVIDER_OFFLINE, List.of(), List.of(sarahBack));
        SchedulingContext cancelled = new SchedulingContext(returnedVisit, TriggerReason.SLOT_CANCELLED, List.of(), List.of(sarahBack));

        String offlinePrompt = prompts.forContext(offline);
        String returnedPrompt = prompts.forContext(returned);
        String cancelledPrompt = prompts.forContext(cancelled);

        assertTrue(offlinePrompt.contains("cannot be chosen"));
        assertTrue(returnedPrompt.contains("same provider"));
        assertFalse(returnedPrompt.contains("cannot be chosen"));
        assertTrue(cancelledPrompt.contains("same provider"));
        assertFalse(offlinePrompt.equals(cancelledPrompt));
        assertTrue(offlinePrompt.contains("John Martinez"));
        assertTrue(offlinePrompt.contains("ROUTINE"));
    }
}
