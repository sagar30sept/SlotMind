package com.slotmind.api;

import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;

import java.time.Instant;

public record AppointmentResponse(
        String id,
        String patientName,
        AppointmentType appointmentType,
        String providerId,
        String providerName,
        String slotId,
        Instant slotStart,
        int durationMinutes,
        AppointmentStatus status,
        SuggestionResponse suggestion,
        String lastError) {
}
