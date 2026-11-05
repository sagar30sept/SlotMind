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

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "appointments")
public class Appointment {

    /** How early before the slot starts the desk may check a patient in. */
    public static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofMinutes(60);

    @Id
    private String id;

    // Eager: scheduling strategies read the patient's name after the loading transaction closes.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

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

    @Column(name = "cancel_reason", length = 280)
    private String cancelReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Version
    private Long version;

    protected Appointment() {
    }

    public Appointment(String id, Patient patient, AppointmentType appointmentType, Provider provider, Slot slot) {
        this.id = id;
        this.patient = patient;
        this.appointmentType = appointmentType;
        this.provider = provider;
        this.slot = slot;
        this.status = AppointmentStatus.CONFIRMED;
        this.statusChangedAt = Instant.now();
    }

    /** Moves a booked appointment into waiting for a desk decision. */
    public void markReschedulePending() {
        if (!AppointmentStatus.MOVABLE.contains(status)) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + id + " cannot wait for reschedule from " + status);
        }
        moveTo(AppointmentStatus.RESCHEDULE_PENDING, Instant.now());
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
        moveTo(AppointmentStatus.RESCHEDULED, Instant.now());
    }

    /** Cancels a visit that has not started. The caller frees the slot. */
    public void cancel(String reason, Instant now) {
        if (status != AppointmentStatus.CONFIRMED
                && status != AppointmentStatus.RESCHEDULE_PENDING
                && status != AppointmentStatus.RESCHEDULED) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + id + " cannot be cancelled from " + status);
        }
        this.cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
        moveTo(AppointmentStatus.CANCELLED, now);
    }

    /** Records that the patient arrived, from an hour before the slot until it ends. */
    public void checkIn(Instant now) {
        requireMovable("checked in");
        Instant opens = slot.getStartTime().minus(CHECK_IN_OPENS_BEFORE);
        Instant closes = slot.getStartTime().plus(Duration.ofMinutes(slot.getDurationMinutes()));
        if (now.isBefore(opens)) {
            throw new DomainException(
                    "TOO_EARLY_TO_CHECK_IN",
                    "Check-in opens " + CHECK_IN_OPENS_BEFORE.toMinutes() + " minutes before the visit");
        }
        if (now.isAfter(closes)) {
            throw new DomainException("VISIT_ALREADY_ENDED", "This visit's time has already passed");
        }
        this.checkedInAt = now;
        moveTo(AppointmentStatus.CHECKED_IN, now);
    }

    /** Marks a checked-in visit as finished. */
    public void markCompleted(Instant now) {
        if (status != AppointmentStatus.CHECKED_IN) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Only a checked-in appointment can be completed");
        }
        moveTo(AppointmentStatus.COMPLETED, now);
    }

    /** Marks a visit the patient missed. Only allowed once the slot has started. */
    public void markNoShow(Instant now) {
        requireMovable("marked as a no-show");
        if (now.isBefore(slot.getStartTime())) {
            throw new DomainException(
                    "VISIT_NOT_STARTED",
                    "A visit can only be marked as a no-show after it starts");
        }
        moveTo(AppointmentStatus.NO_SHOW, now);
    }

    private void requireMovable(String action) {
        if (!AppointmentStatus.MOVABLE.contains(status)) {
            throw new DomainException(
                    "ILLEGAL_TRANSITION",
                    "Appointment " + id + " cannot be " + action + " from " + status);
        }
    }

    private void moveTo(AppointmentStatus next, Instant at) {
        this.status = next;
        this.statusChangedAt = at;
    }

    public String getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    /** The patient's display name, used in spoken reasons and prompts. */
    public String getPatientName() {
        return patient.getFullName();
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

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Long getVersion() {
        return version;
    }
}
