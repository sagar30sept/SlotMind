package com.slotmind.scheduling;

public record Recommendation(
        String slotId,
        String alternateProviderId,
        double confidence,
        String reasoning) {
}
