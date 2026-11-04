package com.slotmind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.scheduling.Recommendation;
import com.slotmind.scheduling.SchedulingContext;
import com.slotmind.scheduling.SpokenReason;

public class LlmSuggestionParser {

    private final ObjectMapper objectMapper;

    public LlmSuggestionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the model JSON and rejects a slot that is not open in the current context.
     * Jargon in the reasoning is replaced with a sentence the desk can read aloud.
     */
    public Recommendation parse(String raw, SchedulingContext context) {
        if (raw == null || raw.isBlank()) {
            throw new LlmException("Empty model response");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("Model response did not contain JSON");
        }
        try {
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            String slotId = text(node, "slotId");
            String alternate = text(node, "alternateProviderId");
            if (alternate != null && (alternate.isBlank() || "null".equalsIgnoreCase(alternate))) {
                alternate = null;
            }
            double confidence = node.path("confidence").isNumber() ? node.path("confidence").asDouble() : 0.5;
            String reasoning = text(node, "reasoning");
            Slot slot = context.availableSlots().stream()
                    .filter(candidate -> candidate.getId().equals(slotId))
                    .findFirst()
                    .orElse(null);
            if (slot == null || slot.getStatus() != SlotStatus.OPEN || slot.getProvider().getStatus() == ProviderStatus.OFFLINE) {
                throw new LlmException("Recommended slot is not an open slot in the current data");
            }
            if (alternate != null) {
                String providerId = alternate;
                boolean known = context.roster().stream().anyMatch(provider -> provider.getId().equals(providerId));
                if (!known || !slot.getProvider().getId().equals(alternate)) {
                    throw new LlmException("Alternate provider does not match the recommended slot");
                }
            }
            if (SpokenReason.hasJargon(reasoning)) {
                reasoning = SpokenReason.sentence(
                        context.appointment().getPatientName(),
                        slot.getStartTime(),
                        slot.getProvider().getName());
            }
            return new Recommendation(slot.getId(), alternate, confidence, reasoning);
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException("Could not parse model response", exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
