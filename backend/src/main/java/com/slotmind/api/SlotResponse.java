package com.slotmind.api;

import com.slotmind.domain.SlotStatus;

import java.time.Instant;

public record SlotResponse(
        String id,
        String providerId,
        String providerName,
        Instant startTime,
        int durationMinutes,
        SlotStatus status) {
}
