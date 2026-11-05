package com.slotmind.api;

import com.slotmind.domain.AppointmentType;

import java.time.Instant;

public record WaitlistResponse(
        String id,
        String patientId,
        String patientName,
        AppointmentType appointmentType,
        String preferredProviderId,
        String preferredProviderName,
        String note,
        Instant createdAt,
        int matchingOpenSlots) {
}
