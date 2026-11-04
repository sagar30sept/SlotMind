package com.slotmind.application;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.ProviderResponse;
import com.slotmind.api.SlotResponse;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeskQueryService {

    private final AppointmentRepository appointments;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentViews views;

    public DeskQueryService(
            AppointmentRepository appointments,
            ProviderRepository providers,
            SlotRepository slots,
            AppointmentViews views) {
        this.appointments = appointments;
        this.providers = providers;
        this.slots = slots;
        this.views = views;
    }

    /** Lists appointments, optionally filtered by status, with the latest suggestion attached. */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> appointments(AppointmentStatus status) {
        var rows = status == null
                ? appointments.findAllWithDetails()
                : appointments.findByStatusWithDetails(status);
        return views.list(rows);
    }

    /** Lists every provider and their current status. */
    @Transactional(readOnly = true)
    public List<ProviderResponse> providers() {
        return providers.findAll().stream().map(views::toProvider).toList();
    }

    /** Lists every slot with its provider. */
    @Transactional(readOnly = true)
    public List<SlotResponse> slots() {
        return slots.findAllWithProvider().stream().map(views::toSlot).toList();
    }
}
