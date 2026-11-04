package com.slotmind.scheduling;

import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RuleBasedStrategy implements SchedulingStrategy {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedStrategy.class);

    @Override
    public String name() {
        return "rule";
    }

    /** Prefers the same provider's earliest slots, then another provider in the same specialty. */
    @Override
    public List<Recommendation> recommend(SchedulingContext context) {
        Provider current = context.appointment().getProvider();
        List<Slot> sameProvider = context.availableSlots().stream()
                .filter(slot -> slot.getProvider().getId().equals(current.getId()))
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
        List<Slot> sameSpecialty = context.availableSlots().stream()
                .filter(slot -> !slot.getProvider().getId().equals(current.getId()))
                .filter(slot -> slot.getProvider().getSpecialty() == current.getSpecialty())
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        List<Recommendation> ordered = new ArrayList<>();
        for (Slot slot : sameProvider) {
            ordered.add(toRecommendation(context, slot, 0.8));
        }
        for (Slot slot : sameSpecialty) {
            ordered.add(toRecommendation(context, slot, 0.55));
        }
        log.debug("Rule ranking for appointment {} returned {}",
                context.appointment().getId(), ordered.stream().map(Recommendation::slotId).toList());
        return ordered;
    }

    /** Turns a slot into a recommendation the desk can read aloud. */
    private Recommendation toRecommendation(SchedulingContext context, Slot slot, double confidence) {
        boolean sameProvider = slot.getProvider().getId().equals(context.appointment().getProvider().getId());
        String alternate = sameProvider ? null : slot.getProvider().getId();
        String reasoning = SpokenReason.sentence(
                context.appointment().getPatientName(),
                slot.getStartTime(),
                slot.getProvider().getName());
        return new Recommendation(slot.getId(), alternate, confidence, reasoning);
    }
}
