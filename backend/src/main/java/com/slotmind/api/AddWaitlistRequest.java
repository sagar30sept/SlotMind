package com.slotmind.api;

import com.slotmind.domain.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddWaitlistRequest(
        @NotBlank String patientName,
        @NotNull AppointmentType appointmentType,
        String preferredProviderId,
        @Size(max = 280) String note) {
}
