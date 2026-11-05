package com.slotmind.api;

import java.time.LocalDate;

public record PatientResponse(
        String id,
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth) {
}
