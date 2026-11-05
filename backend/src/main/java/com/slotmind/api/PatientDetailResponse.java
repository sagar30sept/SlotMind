package com.slotmind.api;

import java.util.List;

public record PatientDetailResponse(
        PatientResponse patient,
        List<AppointmentResponse> appointments) {
}
