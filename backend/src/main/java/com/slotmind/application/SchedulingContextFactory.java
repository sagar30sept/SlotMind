package com.slotmind.application;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SlotRepository;
import com.slotmind.scheduling.SchedulingContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SchedulingContextFactory {

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final ProviderRepository providers;

    public SchedulingContextFactory(
            AppointmentRepository appointments,
            SlotRepository slots,
            ProviderRepository providers) {
        this.appointments = appointments;
        this.slots = slots;
        this.providers = providers;
    }

    /** Loads the appointment, future open slots of online providers, and the provider roster. */
    @Transactional(readOnly = true)
    public SchedulingContext build(String appointmentId, TriggerReason trigger) {
        Appointment appointment = appointments.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Appointment " + appointmentId + " was not found"));
        List<com.slotmind.domain.Slot> available = slots.findByStatusWithProvider(SlotStatus.OPEN).stream()
                .filter(slot -> slot.getStartTime().isAfter(Instant.now()))
                .filter(slot -> slot.getProvider().getStatus() != ProviderStatus.OFFLINE)
                .toList();
        return new SchedulingContext(appointment, trigger, available, providers.findAll());
    }
}
