package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.BookAppointmentRequest;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.Ids;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.Patient;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final AppointmentViews views;
    private final PatientService patients;
    private final PatientScheduleGuard guard;

    public BookingService(
            ProviderRepository providers,
            SlotRepository slots,
            AppointmentRepository appointments,
            AppointmentViews views,
            PatientService patients,
            PatientScheduleGuard guard) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.views = views;
        this.patients = patients;
        this.guard = guard;
    }

    /**
     * Books an open slot for an online provider and returns the confirmed appointment.
     * The slot must belong to that provider, and the patient must not already have a visit at that time.
     */
    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        Patient patient = patients.require(request.patientId());
        Provider provider = providers.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Provider " + request.providerId() + " was not found"));
        Slot slot = slots.findWithProviderById(request.slotId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Slot " + request.slotId() + " was not found"));
        if (provider.isOffline()) {
            throw new DomainException("PROVIDER_OFFLINE", provider.getName() + " is offline");
        }
        if (!slot.getProvider().getId().equals(provider.getId())) {
            throw new DomainException("PROVIDER_MISMATCH", "That slot belongs to " + slot.getProvider().getName());
        }
        if (!slot.getStartTime().isAfter(Instant.now())) {
            throw new DomainException("SLOT_IN_PAST", "That slot has already started");
        }
        guard.requireFree(patient, slot, null);
        slot.book();
        Appointment appointment = appointments.save(new Appointment(
                Ids.next("APT-"),
                patient,
                request.appointmentType(),
                provider,
                slot));
        log.info("Booked appointment {} for patient {} on slot {} with {}",
                appointment.getId(), patient.getId(), slot.getId(), provider.getId());
        return views.toResponse(appointment, null);
    }
}
