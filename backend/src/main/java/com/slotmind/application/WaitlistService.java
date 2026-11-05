package com.slotmind.application;

import com.slotmind.api.AddWaitlistRequest;
import com.slotmind.api.WaitlistResponse;
import com.slotmind.domain.Ids;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.Patient;
import com.slotmind.domain.Provider;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.WaitlistEntry;
import com.slotmind.persistence.PatientRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SlotRepository;
import com.slotmind.persistence.WaitlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.slotmind.domain.AppointmentType.FOLLOW_UP;
import static com.slotmind.domain.AppointmentType.ROUTINE;
import static com.slotmind.domain.AppointmentType.URGENT;

@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository entries;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final PatientRepository patients;

    public WaitlistService(
            WaitlistRepository entries,
            ProviderRepository providers,
            SlotRepository slots,
            PatientRepository patients) {
        this.entries = entries;
        this.providers = providers;
        this.slots = slots;
        this.patients = patients;
    }

    /** Lists waiting patients and how many currently open slots match each preference. */
    @Transactional(readOnly = true)
    public List<WaitlistResponse> list() {
        List<Slot> open = slots.findByStatusWithProvider(SlotStatus.OPEN);
        return entries.findAllWithProvider().stream()
                .map(entry -> toResponse(entry, open))
                .toList();
    }

    /** Adds a patient who is waiting for an opening. */
    @Transactional
    public WaitlistResponse add(AddWaitlistRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Patient " + request.patientId() + " was not found"));
        Provider preferred = preferredProvider(request.preferredProviderId());
        String note = blankToNull(request.note());
        WaitlistEntry saved = entries.save(new WaitlistEntry(
                Ids.next("WLT-"),
                patient,
                request.appointmentType(),
                preferred,
                note,
                Instant.now()));
        log.info("Added patient {} to the waitlist as {}", patient.getId(), saved.getId());
        return toResponse(saved, slots.findByStatusWithProvider(SlotStatus.OPEN));
    }

    /** Removes a patient from the waitlist. */
    @Transactional
    public void remove(String id) {
        WaitlistEntry entry = entries.findById(id)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Waitlist entry " + id + " was not found"));
        entries.delete(entry);
        log.info("Removed {} from the waitlist", id);
    }

    /** Inserts sample waitlist rows when the table is empty. */
    @Transactional
    public void seedIfEmpty() {
        if (entries.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        entries.save(new WaitlistEntry(
                "WLT-001",
                seedPatient("PAT-005", "Nora Alvarez", "+1-555-0105", now),
                URGENT,
                providers.findById("PRV-004").orElse(null),
                "Needs a same-day visit",
                now.minus(3, ChronoUnit.HOURS)));
        entries.save(new WaitlistEntry(
                "WLT-002",
                seedPatient("PAT-006", "Samir Das", "+1-555-0106", now),
                ROUTINE,
                null,
                "Called for the next opening",
                now.minus(2, ChronoUnit.HOURS)));
        entries.save(new WaitlistEntry(
                "WLT-003",
                seedPatient("PAT-007", "Helen Cho", "+1-555-0107", now),
                FOLLOW_UP,
                providers.findById("PRV-005").orElse(null),
                "Follow-up from last week",
                now.minus(1, ChronoUnit.HOURS)));
        log.info("Seeded the waitlist");
    }

    /** Reuses the seeded patient row, or creates it when data.sql predates the patient table. */
    private Patient seedPatient(String id, String name, String phone, Instant now) {
        return patients.findById(id)
                .or(() -> patients.findFirstByFullNameIgnoreCase(name))
                .orElseGet(() -> patients.save(new Patient(id, name, phone, null, null, now)));
    }

    private Provider preferredProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return providers.findById(providerId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Provider " + providerId + " was not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static WaitlistResponse toResponse(WaitlistEntry entry, List<Slot> open) {
        Provider preferred = entry.getPreferredProvider();
        int matching = (int) open.stream()
                .filter(slot -> preferred == null || slot.getProvider().getId().equals(preferred.getId()))
                .count();
        return new WaitlistResponse(
                entry.getId(),
                entry.getPatient().getId(),
                entry.getPatientName(),
                entry.getAppointmentType(),
                preferred == null ? null : preferred.getId(),
                preferred == null ? null : preferred.getName(),
                entry.getNote(),
                entry.getCreatedAt(),
                matching);
    }
}
