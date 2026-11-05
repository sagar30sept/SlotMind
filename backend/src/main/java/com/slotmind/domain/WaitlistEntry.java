package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false)
    private AppointmentType appointmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_provider_id")
    private Provider preferredProvider;

    @Column(length = 280)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(
            String id,
            Patient patient,
            AppointmentType appointmentType,
            Provider preferredProvider,
            String note,
            Instant createdAt) {
        this.id = id;
        this.patient = patient;
        this.appointmentType = appointmentType;
        this.preferredProvider = preferredProvider;
        this.note = note;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getPatientName() {
        return patient.getFullName();
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public Provider getPreferredProvider() {
        return preferredProvider;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
