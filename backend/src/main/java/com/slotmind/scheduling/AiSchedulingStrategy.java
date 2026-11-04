package com.slotmind.scheduling;

import com.slotmind.ai.LlmGateway;
import com.slotmind.ai.LlmSuggestionParser;
import com.slotmind.ai.PromptFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiSchedulingStrategy implements SchedulingStrategy {

    private static final Logger log = LoggerFactory.getLogger(AiSchedulingStrategy.class);

    private final LlmGateway gateway;
    private final PromptFactory prompts;
    private final LlmSuggestionParser parser;
    private final RuleBasedStrategy rules;

    public AiSchedulingStrategy(
            LlmGateway gateway,
            PromptFactory prompts,
            LlmSuggestionParser parser,
            RuleBasedStrategy rules) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.parser = parser;
        this.rules = rules;
    }

    @Override
    public String name() {
        return "ai";
    }

    /**
     * Asks Gemini for a pick and falls back to the rule list when the model call fails.
     */
    @Override
    public List<Recommendation> recommend(SchedulingContext context) {
        List<Recommendation> ruleList = rules.recommend(context);
        String appointmentId = context.appointment().getId();
        try {
            String raw = gateway.call(prompts.forContext(context));
            Recommendation pick = parser.parse(raw, context);
            log.info("Gemini selected slot {} for appointment {} confidence {}",
                    pick.slotId(), appointmentId, pick.confidence());
            return withPickFirst(pick, ruleList);
        } catch (RuntimeException exception) {
            log.error("Gemini ranking failed for appointment {}. Using the rule list.", appointmentId, exception);
            return ruleList;
        }
    }

    /** Puts the model pick first and keeps the remaining rule-ranked slots after it. */
    public static List<Recommendation> withPickFirst(Recommendation pick, List<Recommendation> ruleList) {
        List<Recommendation> ordered = new ArrayList<>();
        ordered.add(pick);
        for (Recommendation recommendation : ruleList) {
            if (!recommendation.slotId().equals(pick.slotId())) {
                ordered.add(recommendation);
            }
        }
        return ordered;
    }
}
