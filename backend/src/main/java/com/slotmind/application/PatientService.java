package com.slotmind.application;

import com.slotmind.api.CreatePatientRequest;
import com.slotmind.api.PatientDetailResponse;
import com.slotmind.api.PatientResponse;
import com.slotmind.domain.DomainException;
import com.slotmind.domain.Ids;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.Patient;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private static final int SEARCH_LIMIT = 20;

    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final AppointmentViews views;

    public PatientService(PatientRepository patients, AppointmentRepository appointments, AppointmentViews views) {
        this.patients = patients;
        this.appointments = appointments;
        this.views = views;
    }

    /** Finds up to 20 patients by name or phone; a blank query lists the first 20 alphabetically. */
    @Transactional(readOnly = true)
    public List<PatientResponse> search(String query) {
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT);
        List<Patient> found = query == null || query.isBlank()
                ? patients.findAllByOrderByFullNameAsc(page)
                : patients.search(query.trim(), page);
        return found.stream().map(views::toPatient).toList();
    }

    /** Registers a patient. A phone number already on file is a conflict that names the existing record. */
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        String phone = request.phone().trim();
        patients.findByPhone(phone).ifPresent(existing -> {
            throw new DomainException(
                    "PATIENT_EXISTS",
                    "That phone number belongs to " + existing.getFullName() + " (" + existing.getId() + ")");
        });
        Patient saved = patients.save(new Patient(
                Ids.next("PAT-"),
                request.fullName(),
                phone,
                request.email(),
                request.dateOfBirth(),
                Instant.now()));
        log.info("Registered patient {}", saved.getId());
        return views.toPatient(saved);
    }

    /** Returns a patient with every visit, latest first. */
    @Transactional(readOnly = true)
    public PatientDetailResponse get(String id) {
        Patient patient = require(id);
        return new PatientDetailResponse(
                views.toPatient(patient),
                views.list(appointments.findHistoryByPatient(id)));
    }

    /** Loads a patient or throws NOT_FOUND. */
    @Transactional(readOnly = true)
    public Patient require(String id) {
        return patients.findById(id)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Patient " + id + " was not found"));
    }
}
