package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    private String id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(length = 32)
    private String phone;

    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Patient() {
    }

    /** Registers a patient. Name and phone are required so the desk can reach them. */
    public Patient(String id, String fullName, String phone, String email, LocalDate dateOfBirth, Instant createdAt) {
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("INVALID_PATIENT", "A patient needs a name");
        }
        if (phone == null || phone.isBlank()) {
            throw new DomainException("INVALID_PATIENT", "A patient needs a phone number");
        }
        this.id = id;
        this.fullName = fullName.trim();
        this.phone = phone.trim();
        this.email = email == null || email.isBlank() ? null : email.trim();
        this.dateOfBirth = dateOfBirth;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
