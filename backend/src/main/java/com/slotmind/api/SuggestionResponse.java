package com.slotmind.api;

import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;

import java.time.Instant;

public record SuggestionResponse(
        String suggestionId,
        String recommendedSlotId,
        Instant recommendedSlotStart,
        String providerName,
        String alternateProviderId,
        double confidence,
        String reasoning,
        TriggerReason triggerReason,
        SuggestionStatus status) {
}
