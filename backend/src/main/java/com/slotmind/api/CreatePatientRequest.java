package com.slotmind.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePatientRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Pattern(regexp = "^[+0-9][0-9 ()-]{6,31}$", message = "must be a phone number") String phone,
        @Email @Size(max = 255) String email,
        @Past LocalDate dateOfBirth) {
}
