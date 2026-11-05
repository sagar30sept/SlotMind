package com.slotmind.api;

import com.slotmind.application.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PatientController {

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    /** Searches patients by name or phone. */
    @GetMapping("/patients")
    public List<PatientResponse> search(@RequestParam(name = "q", required = false) String query) {
        return patients.search(query);
    }

    /** Registers a new patient. */
    @PostMapping("/patients")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.status(201).body(patients.create(request));
    }

    /** Returns a patient and their visit history. */
    @GetMapping("/patients/{id}")
    public PatientDetailResponse get(@PathVariable String id) {
        return patients.get(id);
    }
}
