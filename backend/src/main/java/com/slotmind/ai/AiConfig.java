package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public PromptFactory promptFactory() {
        return new PromptFactory();
    }

    @Bean
    public LlmSuggestionParser llmSuggestionParser(ObjectMapper objectMapper) {
        return new LlmSuggestionParser(objectMapper);
    }
}
