package com.slotmind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.agentic.OutboxWorker;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.JobStatus;
import com.slotmind.domain.JobType;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.PatientRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AppointmentLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxWorker worker;

    @Autowired
    private OutboxJobRepository jobs;

    @Autowired
    private SlotRepository slots;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private ProviderRepository providers;

    @Test
    void aPatientCannotHoldTwoOverlappingVisits() throws Exception {
        book("PAT-008", "PRV-001", "SLT-1000").andExpect(status().isCreated());

        // Same 09:00 start with a different provider.
        book("PAT-008", "PRV-002", "SLT-1080")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_DOUBLE_BOOKED"));
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-1080").orElseThrow().getStatus());

        // 09:30 starts as the 09:00 visit ends, so it does not overlap.
        book("PAT-008", "PRV-001", "SLT-1001").andExpect(status().isCreated());
        // Another patient may take the 09:00 slot with the other provider.
        book("PAT-009", "PRV-002", "SLT-1080").andExpect(status().isCreated());
    }

    @Test
    void bookingNeedsAKnownPatient() throws Exception {
        book("PAT-NOPE", "PRV-004", "SLT-005")
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellingAConfirmedVisitReopensItsSlot() throws Exception {
        mockMvc.perform(post("/appointments/APT-002/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Patient called to cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Patient called to cancel"));
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-002").orElseThrow().getStatus());

        book("PAT-009", "PRV-001", "SLT-002").andExpect(status().isCreated());

        mockMvc.perform(post("/appointments/APT-002/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
    }

    @Test
    void cancellingAWaitingVisitWithdrawsItsProposalAndSkipsQueuedWork() throws Exception {
        setProviderStatus("PRV-001", "OFFLINE");
        runFanout(JobType.FANOUT_PROVIDER_OFFLINE);
        OutboxJob forJohn = suggestJobFor("APT-001");
        OutboxJob forEmily = suggestJobFor("APT-002");
        worker.applySuggestion(forJohn.getId());
        assertEquals("PENDING", appointment("APT-001").path("suggestion").path("status").asText());

        mockMvc.perform(post("/appointments/APT-001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("REJECTED", appointment("APT-001").path("suggestion").path("status").asText());

        // Emily's job was queued before her visit was cancelled; it finishes quietly.
        mockMvc.perform(post("/appointments/APT-002/cancel")).andExpect(status().isOk());
        jobs.claim(forEmily.getId(), worker.workerId(), Instant.now().plusSeconds(30), Instant.now());
        worker.applySuggestion(forEmily.getId());
        assertEquals(JobStatus.DONE, jobs.findById(forEmily.getId()).orElseThrow().getStatus());
        assertEquals(true, appointment("APT-002").path("lastError").isNull());
    }

    @Test
    void checkInThenCompleteFollowsTheVisit() throws Exception {
        mockMvc.perform(post("/appointments/APT-002/complete"))
                .andExpect(status().isConflict());

        // SLT-006 starts four hours out, so check-in is not open yet.
        mockMvc.perform(post("/appointments/APT-004/check-in"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_EARLY_TO_CHECK_IN"));

        mockMvc.perform(post("/appointments/APT-001/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty());
        mockMvc.perform(post("/appointments/APT-001/cancel"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/appointments/APT-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/appointments/APT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void noShowOnlyAfterTheVisitStarts() throws Exception {
        mockMvc.perform(post("/appointments/APT-001/no-show"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_NOT_STARTED"));

        Slot started = slots.save(new Slot(
                "SLT-PAST",
                providers.findById("PRV-004").orElseThrow(),
                Instant.now().minus(10, ChronoUnit.MINUTES),
                30,
                SlotStatus.BOOKED));
        appointments.save(new Appointment(
                "APT-PAST",
                patients.findById("PAT-008").orElseThrow(),
                AppointmentType.ROUTINE,
                started.getProvider(),
                started));

        mockMvc.perform(post("/appointments/APT-PAST/no-show"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-PAST").orElseThrow().getStatus());

        book("PAT-009", "PRV-004", "SLT-PAST")
                .andExpect(status().isConflict());
    }

    @Test
    void theDeskCanMoveAVisitThatWasAlreadyRescheduled() throws Exception {
        moveOnce("APT-002");
        JsonNode moved = appointment("APT-002");
        assertEquals("RESCHEDULED", moved.path("status").asText());
        String firstSlot = moved.path("slotId").asText();
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-002").orElseThrow().getStatus());

        mockMvc.perform(post("/appointments/APT-002/reschedule"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RESCHEDULE_PENDING"));
        OutboxJob job = suggestJobFor("APT-002");
        jobs.claim(job.getId(), worker.workerId(), Instant.now().plusSeconds(30), Instant.now());
        worker.applySuggestion(job.getId());

        JsonNode proposal = appointment("APT-002").path("suggestion");
        assertEquals("PENDING", proposal.path("status").asText());
        assertEquals("INITIAL", proposal.path("triggerReason").asText());
        String secondSlot = proposal.path("recommendedSlotId").asText();
        assertNotEquals(firstSlot, secondSlot);

        decide(proposal.path("suggestionId").asText(), "ACCEPT").andExpect(status().isOk());
        assertEquals(secondSlot, appointment("APT-002").path("slotId").asText());
        assertEquals(SlotStatus.OPEN, slots.findById(firstSlot).orElseThrow().getStatus());
        assertEquals(SlotStatus.BOOKED, slots.findById(secondSlot).orElseThrow().getStatus());

        mockMvc.perform(post("/appointments/APT-003/cancel")).andExpect(status().isOk());
        mockMvc.perform(post("/appointments/APT-003/reschedule"))
                .andExpect(status().isConflict());
    }

    @Test
    void anOfflineProviderAlsoMovesVisitsThatWereRescheduledOntoThem() throws Exception {
        moveOnce("APT-002");
        assertEquals("PRV-001", appointment("APT-002").path("providerId").asText());

        setProviderStatus("PRV-001", "OFFLINE");
        runFanout(JobType.FANOUT_PROVIDER_OFFLINE);

        suggestJobFor("APT-002");
        assertEquals("RESCHEDULE_PENDING", appointment("APT-002").path("status").asText());
    }

    @Test
    void acceptingAProposalCannotDoubleBookThePatient() throws Exception {
        // Emily also holds the 09:00 visit that the desk proposal for APT-002 will point at.
        book("PAT-002", "PRV-001", "SLT-1000").andExpect(status().isCreated());
        JsonNode proposal = objectMapper.readTree(mockMvc.perform(post("/appointments/APT-002/suggest"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("suggestion");
        assertEquals("SLT-1001", proposal.path("recommendedSlotId").asText());

        // Book Emily into 09:30 elsewhere so the proposal now clashes.
        book("PAT-002", "PRV-002", "SLT-1081").andExpect(status().isCreated());
        decide(proposal.path("suggestionId").asText(), "ACCEPT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_DOUBLE_BOOKED"));
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-1001").orElseThrow().getStatus());
    }

    /** Desk-suggests and accepts a move for the appointment. */
    private void moveOnce(String appointmentId) throws Exception {
        String suggestionId = objectMapper.readTree(mockMvc.perform(post("/appointments/" + appointmentId + "/suggest"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("suggestion").path("suggestionId").asText();
        decide(suggestionId, "ACCEPT").andExpect(status().isOk());
    }

    private ResultActions book(String patientId, String providerId, String slotId) throws Exception {
        return mockMvc.perform(post("/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"patientId":"%s","appointmentType":"ROUTINE","providerId":"%s","slotId":"%s"}
                        """.formatted(patientId, providerId, slotId)));
    }

    private ResultActions decide(String suggestionId, String action) throws Exception {
        return mockMvc.perform(patch("/suggestions/" + suggestionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"));
    }

    private void setProviderStatus(String providerId, String status) throws Exception {
        mockMvc.perform(patch("/providers/" + providerId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private void runFanout(JobType type) {
        String id = jobs.findAll().stream()
                .filter(job -> job.getJobType() == type)
                .findFirst()
                .orElseThrow()
                .getId();
        jobs.claim(id, worker.workerId(), Instant.now().plusSeconds(30), Instant.now());
        worker.process(id);
    }

    private OutboxJob suggestJobFor(String appointmentId) {
        List<OutboxJob> found = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .filter(job -> job.getPayload().contains("\"" + appointmentId + "\""))
                .toList();
        assertEquals(1, found.size(), "suggest jobs for " + appointmentId);
        return found.get(0);
    }

    private JsonNode appointment(String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/appointments/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
