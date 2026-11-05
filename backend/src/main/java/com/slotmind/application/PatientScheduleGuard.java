package com.slotmind.application;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.Patient;
import com.slotmind.domain.Slot;
import com.slotmind.persistence.AppointmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Stops one patient from holding two visits at overlapping times. */
@Component
public class PatientScheduleGuard {

    private final AppointmentRepository appointments;

    public PatientScheduleGuard(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    /**
     * Throws PATIENT_DOUBLE_BOOKED when the patient already has an active visit overlapping the slot.
     * The appointment being moved, if any, is ignored.
     */
    public void requireFree(Patient patient, Slot slot, String movingAppointmentId) {
        Instant start = slot.getStartTime();
        Instant end = end(slot);
        appointments.findByPatientAndStatusIn(patient.getId(), AppointmentStatus.ACTIVE).stream()
                .filter(existing -> !existing.getId().equals(movingAppointmentId))
                .filter(existing -> existing.getSlot().getStartTime().isBefore(end)
                        && start.isBefore(end(existing.getSlot())))
                .findFirst()
                .ifPresent(clash -> {
                    throw new DomainException(
                            "PATIENT_DOUBLE_BOOKED",
                            patient.getFullName() + " already has a visit at that time (" + clash.getId() + ")");
                });
    }

    private static Instant end(Slot slot) {
        return slot.getStartTime().plusSeconds(slot.getDurationMinutes() * 60L);
    }

    /** Convenience for callers that already hold the appointment. */
    public void requireFree(Appointment moving, Slot target) {
        requireFree(moving.getPatient(), target, moving.getId());
    }
}
