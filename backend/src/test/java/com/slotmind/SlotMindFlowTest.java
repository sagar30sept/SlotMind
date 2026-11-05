package com.slotmind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.agentic.OutboxWorker;
import com.slotmind.domain.JobType;
import com.slotmind.domain.SlotStatus;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.SlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SlotMindFlowTest {

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

    @Test
    void secondBookOfTheSameSlotConflicts() throws Exception {
        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"PAT-008","appointmentType":"ROUTINE","providerId":"PRV-004","slotId":"SLT-005"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"PAT-009","appointmentType":"FOLLOW_UP","providerId":"PRV-004","slotId":"SLT-005"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void suggestIsIdempotentAndKeepsTheSlotOpen() throws Exception {
        MvcResult first = mockMvc.perform(post("/appointments/APT-001/suggest"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(first.getResponse().getContentAsString());
        String suggestionId = body.path("suggestion").path("suggestionId").asText();

        mockMvc.perform(post("/appointments/APT-001/suggest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(suggestionId)));

        assertEquals("SLT-1000", body.path("suggestion").path("recommendedSlotId").asText());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-1000").orElseThrow().getStatus());
    }

    @Test
    void offlineFanoutIsIdempotentAndAcceptPreventsDoubleBooking() throws Exception {
        mockMvc.perform(patch("/providers/PRV-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        assertEquals(0, jobs.countByJobType(JobType.SUGGEST));

        String fanoutId = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.FANOUT_PROVIDER_OFFLINE)
                .findFirst()
                .orElseThrow()
                .getId();
        claim(fanoutId);
        worker.process(fanoutId);
        worker.process(fanoutId);
        assertEquals(2, jobs.countByJobType(JobType.SUGGEST));

        mockMvc.perform(patch("/providers/PRV-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        assertEquals(1, jobs.findAll().stream().filter(job -> job.getJobType() == JobType.FANOUT_PROVIDER_OFFLINE).count());

        jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .forEach(job -> worker.applySuggestion(job.getId()));
        jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .forEach(job -> worker.applySuggestion(job.getId()));

        JsonNode pending = objectMapper.readTree(mockMvc.perform(get("/appointments?status=RESCHEDULE_PENDING"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals(2, pending.size());
        String firstSuggestion = pending.get(0).path("suggestion").path("suggestionId").asText();
        String secondSuggestion = pending.get(1).path("suggestion").path("suggestionId").asText();

        mockMvc.perform(patch("/suggestions/" + firstSuggestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/suggestions/" + secondSuggestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/suggestions/" + firstSuggestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isConflict());

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/appointments?status=RESCHEDULE_PENDING"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, after.size());
        assertEquals(SlotStatus.BOOKED, slots.findById("SLT-005").orElseThrow().getStatus());
    }

    @Test
    void suggestAnotherSkipsTheRejectedSlotAndKeepsTheVisitWaiting() throws Exception {
        mockMvc.perform(patch("/providers/PRV-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        String fanoutId = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.FANOUT_PROVIDER_OFFLINE)
                .findFirst()
                .orElseThrow()
                .getId();
        claim(fanoutId);
        worker.process(fanoutId);
        jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .forEach(job -> worker.applySuggestion(job.getId()));

        JsonNode pending = objectMapper.readTree(mockMvc.perform(get("/appointments?status=RESCHEDULE_PENDING"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode john = null;
        for (JsonNode appointment : pending) {
            if ("APT-001".equals(appointment.path("id").asText())) {
                john = appointment;
            }
        }
        assertNotNull(john);
        String suggestionId = john.path("suggestion").path("suggestionId").asText();
        assertEquals("SLT-005", john.path("suggestion").path("recommendedSlotId").asText());

        mockMvc.perform(patch("/suggestions/" + suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULE_PENDING"))
                .andExpect(jsonPath("$.suggestion.status").value("REJECTED"));

        MvcResult next = mockMvc.perform(post("/appointments/APT-001/suggest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULE_PENDING"))
                .andExpect(jsonPath("$.suggestion.status").value("PENDING"))
                .andExpect(jsonPath("$.suggestion.triggerReason").value("PROVIDER_OFFLINE"))
                .andExpect(jsonPath("$.suggestion.recommendedSlotId").value("SLT-1240"))
                .andReturn();
        String nextSuggestionId = objectMapper.readTree(next.getResponse().getContentAsString())
                .path("suggestion")
                .path("suggestionId")
                .asText();
        assertNotEquals(suggestionId, nextSuggestionId);

        mockMvc.perform(post("/appointments/APT-001/suggest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion.suggestionId").value(nextSuggestionId));

        assertEquals(SlotStatus.OPEN, slots.findById("SLT-004").orElseThrow().getStatus());
        assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
    }

    @Test
    void availableProviderSuggestsAWaitingVisitThatHadNoMatch() throws Exception {
        mockMvc.perform(patch("/providers/PRV-003/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        String fanoutId = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.FANOUT_PROVIDER_OFFLINE)
                .findFirst()
                .orElseThrow()
                .getId();
        claim(fanoutId);
        worker.process(fanoutId);
        jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .forEach(job -> worker.applySuggestion(job.getId()));

        JsonNode waiting = appointment("APT-003");
        assertEquals("RESCHEDULE_PENDING", waiting.path("status").asText());
        assertEquals(true, waiting.path("suggestion").isNull());

        mockMvc.perform(patch("/providers/PRV-003/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        var retry = jobs.findAll().stream()
                .filter(job -> job.getDedupeKey() != null && job.getDedupeKey().contains("AVAILABLE"))
                .findFirst()
                .orElseThrow();
        worker.applySuggestion(retry.getId());

        JsonNode suggested = appointment("APT-003");
        assertEquals("PENDING", suggested.path("suggestion").path("status").asText());
        assertEquals("SLT-1160", suggested.path("suggestion").path("recommendedSlotId").asText());
        assertEquals("Dr. Priya Sharma", suggested.path("suggestion").path("providerName").asText());

        mockMvc.perform(patch("/providers/PRV-003/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        assertEquals(1, jobs.findAll().stream().filter(job -> job.getDedupeKey().contains("AVAILABLE")).count());
    }

    @Test
    void availableProviderLeavesAnExistingSuggestionInPlace() throws Exception {
        mockMvc.perform(patch("/providers/PRV-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        String fanoutId = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.FANOUT_PROVIDER_OFFLINE)
                .findFirst()
                .orElseThrow()
                .getId();
        claim(fanoutId);
        worker.process(fanoutId);
        jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.SUGGEST)
                .forEach(job -> worker.applySuggestion(job.getId()));
        long suggestJobs = jobs.countByJobType(JobType.SUGGEST);

        mockMvc.perform(patch("/providers/PRV-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());

        assertEquals(suggestJobs, jobs.countByJobType(JobType.SUGGEST));
        assertEquals("PENDING", appointment("APT-001").path("suggestion").path("status").asText());
    }

    @Test
    void cancelledSlotCreatesOneSuggestion() throws Exception {
        mockMvc.perform(patch("/slots/SLT-003/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        String fanoutId = jobs.findAll().stream()
                .filter(job -> job.getJobType() == JobType.FANOUT_SLOT_CANCELLED)
                .findFirst()
                .orElseThrow()
                .getId();
        claim(fanoutId);
        worker.process(fanoutId);
        assertEquals(1, jobs.countByJobType(JobType.SUGGEST));
    }

    @Test
    void suggestStreamEndsWithASuggestionEvent() throws Exception {
        MvcResult async = mockMvc.perform(post("/appointments/APT-001/suggest/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andExpect(content().string(containsString("suggestion")));
    }

    @Test
    void waitlistListsSeededPatientsAndDoesNotBookASlot() throws Exception {
        mockMvc.perform(get("/waitlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].patientName").value("Nora Alvarez"))
                .andExpect(jsonPath("$[0].matchingOpenSlots").value(81))
                .andExpect(jsonPath("$[1].patientName").value("Samir Das"))
                .andExpect(jsonPath("$[1].preferredProviderName").value(nullValue()))
                .andExpect(jsonPath("$[1].matchingOpenSlots").value(402))
                .andExpect(jsonPath("$[2].patientName").value("Helen Cho"))
                .andExpect(jsonPath("$[2].matchingOpenSlots").value(80));

        MvcResult created = mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"PAT-002","appointmentType":"ROUTINE","preferredProviderId":"PRV-004","note":"Afternoon if possible"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredProviderName").value("Dr. Rahul Verma"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get("/slots"))
                .andExpect(jsonPath("$[?(@.id == 'SLT-005')].status").value(contains("OPEN")));

        mockMvc.perform(delete("/waitlist/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/waitlist/" + id))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"   \",\"appointmentType\":\"ROUTINE\"}"))
                .andExpect(status().isBadRequest());
    }

    private void claim(String jobId) {
        jobs.claim(jobId, worker.workerId(), Instant.now().plusSeconds(30), Instant.now());
    }

    private JsonNode appointment(String id) throws Exception {
        JsonNode appointments = objectMapper.readTree(mockMvc.perform(get("/appointments"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        for (JsonNode appointment : appointments) {
            if (id.equals(appointment.path("id").asText())) {
                return appointment;
            }
        }
        throw new AssertionError("Missing appointment " + id);
    }
}
