package com.slotmind.scheduling;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.TriggerReason;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record SchedulingContext(
        Appointment appointment,
        TriggerReason trigger,
        List<Slot> availableSlots,
        List<Provider> roster) {

    /** Returns a copy that omits the given slot ids from the open list. */
    public SchedulingContext withoutSlots(Collection<String> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            return this;
        }
        Set<String> excluded = Set.copyOf(slotIds);
        return new SchedulingContext(
                appointment,
                trigger,
                availableSlots.stream().filter(slot -> !excluded.contains(slot.getId())).toList(),
                roster);
    }
}
