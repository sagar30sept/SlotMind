package com.slotmind.scheduling;

import com.slotmind.config.SlotMindProperties;
import com.slotmind.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(StrategyRegistry.class);

    private final Map<String, SchedulingStrategy> strategies;
    private final SlotMindProperties properties;

    public StrategyRegistry(List<SchedulingStrategy> strategies, SlotMindProperties properties) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(SchedulingStrategy::name, Function.identity()));
        this.properties = properties;
    }

    /** Ranks with the strategy named by {@code slotmind.scheduling.strategy}. */
    public List<Recommendation> recommend(SchedulingContext context) {
        String name = properties.scheduling().strategy();
        SchedulingStrategy strategy = strategies.get(name);
        if (strategy == null) {
            log.error("Unknown scheduling strategy {}", name);
            throw new DomainException("UNKNOWN_STRATEGY", "Unknown scheduling strategy: " + name);
        }
        log.debug("Ranking appointment {} with strategy {} and {} open slots",
                context.appointment().getId(), name, context.availableSlots().size());
        return strategy.recommend(context);
    }

    /** Returns the configured strategy name. */
    public String activeName() {
        return properties.scheduling().strategy();
    }
}
