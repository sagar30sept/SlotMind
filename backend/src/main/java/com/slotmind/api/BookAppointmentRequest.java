package com.slotmind.api;

import com.slotmind.domain.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookAppointmentRequest(
        @NotBlank String patientName,
        @NotNull AppointmentType appointmentType,
        @NotBlank String providerId,
        @NotBlank String slotId) {
}
