package com.slotmind;

import com.slotmind.ai.LlmException;
import com.slotmind.ai.LlmGateway;
import com.slotmind.domain.SlotStatus;
import com.slotmind.persistence.SlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "slotmind.scheduling.strategy=ai",
        "spring.datasource.url=jdbc:h2:mem:slotmind-ai;DB_CLOSE_DELAY=0"
})
class AiFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SlotRepository slots;

    @MockitoBean
    private LlmGateway gateway;

    @Test
    void geminiFailureStillStoresARuleSuggestion() throws Exception {
        when(gateway.call(anyString())).thenThrow(new LlmException("gemini down"));

        mockMvc.perform(post("/appointments/APT-001/suggest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion.status").value("PENDING"))
                .andExpect(jsonPath("$.suggestion.recommendedSlotId").value("SLT-005"))
                .andExpect(jsonPath("$.suggestion.reasoning").value(org.hamcrest.Matchers.containsString("John Martinez")))
                .andExpect(jsonPath("$.suggestion.triggerReason").value("INITIAL"));

        org.junit.jupiter.api.Assertions.assertEquals(SlotStatus.OPEN, slots.findById("SLT-005").orElseThrow().getStatus());
    }
}
