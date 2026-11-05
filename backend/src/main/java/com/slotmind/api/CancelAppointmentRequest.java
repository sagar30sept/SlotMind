package com.slotmind.api;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@Size(max = 280) String reason) {
}
