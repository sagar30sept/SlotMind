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
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    private String id;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false)
    private AppointmentType appointmentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Version
    private Long version;

    protected Appointment() {
    }

    public Appointment(String id, String patientName, AppointmentType appointmentType, Provider provider, Slot slot) {
        this.id = id;
        this.patientName = patientName;
        this.appointmentType = appointmentType;
        this.provider = provider;
        this.slot = slot;
        this.status = AppointmentStatus.CONFIRMED;
    }

    /** Moves a confirmed appointment into waiting for a desk decision. */
    public void markReschedulePending() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + id + " cannot wait for reschedule from " + status);
        }
        status = AppointmentStatus.RESCHEDULE_PENDING;
    }

    /** Points a waiting appointment at the accepted provider and slot. */
    public void rescheduleTo(Provider nextProvider, Slot nextSlot) {
        if (status != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new DomainException(
                    "APPOINTMENT_NOT_PENDING",
                    "Appointment " + id + " is not waiting for the front desk");
        }
        this.provider = nextProvider;
        this.slot = nextSlot;
        this.status = AppointmentStatus.RESCHEDULED;
    }

    /** Marks a rescheduled appointment as finished. */
    public void markCompleted() {
        if (status != AppointmentStatus.RESCHEDULED) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Only a rescheduled appointment can be completed");
        }
        status = AppointmentStatus.COMPLETED;
    }

    public String getId() {
        return id;
    }

    public String getPatientName() {
        return patientName;
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public Provider getProvider() {
        return provider;
    }

    public Slot getSlot() {
        return slot;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
