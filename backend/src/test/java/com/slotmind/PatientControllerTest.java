package com.slotmind;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:slotmind-patients;DB_CLOSE_DELAY=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchMatchesNameOrPhone() throws Exception {
        mockMvc.perform(get("/patients").param("q", "LOVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("PAT-008"))
                .andExpect(jsonPath("$[0].phone").value("+1-555-0108"));

        mockMvc.perform(get("/patients").param("q", "0103"))
                .andExpect(jsonPath("$[0].fullName").value("Aisha Patel"));

        mockMvc.perform(get("/patients"))
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].fullName").value("Ada Lovelace"));
    }

    @Test
    void createRejectsADuplicatePhoneAndBadInput() throws Exception {
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  Mina Shah ","phone":"+1-555-0199","email":"mina@example.com","dateOfBirth":"1992-03-04"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Mina Shah"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Someone Else\",\"phone\":\"+1-555-0199\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_EXISTS"));

        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"No Phone\",\"phone\":\"call me\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID"));

        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Future Baby\",\"phone\":\"+1-555-0177\",\"dateOfBirth\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailListsTheVisitHistory() throws Exception {
        mockMvc.perform(get("/patients/PAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.fullName").value("John Martinez"))
                .andExpect(jsonPath("$.appointments.length()").value(1))
                .andExpect(jsonPath("$.appointments[0].id").value("APT-001"))
                .andExpect(jsonPath("$.appointments[0].patientId").value("PAT-001"));

        mockMvc.perform(get("/patients/PAT-NOPE"))
                .andExpect(status().isNotFound());
    }
}
