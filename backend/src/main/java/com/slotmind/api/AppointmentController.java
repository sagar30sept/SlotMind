package com.slotmind.api;

import com.slotmind.ai.LlmException;
import com.slotmind.ai.LlmGateway;
import com.slotmind.ai.LlmSuggestionParser;
import com.slotmind.ai.PromptFactory;
import com.slotmind.application.BookingService;
import com.slotmind.application.DeskQueryService;
import com.slotmind.application.SuggestService;
import com.slotmind.application.SuggestionWriter;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.AiSchedulingStrategy;
import com.slotmind.scheduling.Recommendation;
import com.slotmind.scheduling.RuleBasedStrategy;
import com.slotmind.scheduling.SchedulingContext;
import com.slotmind.scheduling.StrategyRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@RestController
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final BookingService booking;
    private final DeskQueryService desk;
    private final SuggestService suggestService;
    private final SuggestionWriter writer;
    private final StrategyRegistry registry;
    private final RuleBasedStrategy rules;
    private final LlmGateway gateway;
    private final PromptFactory prompts;
    private final LlmSuggestionParser parser;
    private final Executor suggestStreamExecutor;

    public AppointmentController(
            BookingService booking,
            DeskQueryService desk,
            SuggestService suggestService,
            SuggestionWriter writer,
            StrategyRegistry registry,
            RuleBasedStrategy rules,
            LlmGateway gateway,
            PromptFactory prompts,
            LlmSuggestionParser parser,
            @Qualifier("suggestStreamExecutor") Executor suggestStreamExecutor) {
        this.booking = booking;
        this.desk = desk;
        this.suggestService = suggestService;
        this.writer = writer;
        this.registry = registry;
        this.rules = rules;
        this.gateway = gateway;
        this.prompts = prompts;
        this.parser = parser;
        this.suggestStreamExecutor = suggestStreamExecutor;
    }

    /** Creates a confirmed appointment on an open slot. */
    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(201).body(booking.book(request));
    }

    /** Lists appointments, optionally filtered by status. */
    @GetMapping("/appointments")
    public List<AppointmentResponse> list(@RequestParam(required = false) AppointmentStatus status) {
        return desk.appointments(status);
    }

    /** Returns a stored or newly ranked reschedule suggestion. */
    @PostMapping("/appointments/{id}/suggest")
    public AppointmentResponse suggest(@PathVariable String id) {
        return suggestService.suggest(id);
    }

    /** Streams reasoning tokens, then the stored suggestion, as server-sent events. */
    @PostMapping(value = "/appointments/{id}/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(60_000L);
        suggestStreamExecutor.execute(() -> streamSuggestion(id, emitter));
        return emitter;
    }

    /** Runs the suggest stream off the request thread and completes the emitter. */
    private void streamSuggestion(String id, SseEmitter emitter) {
        try {
            Optional<SuggestionResponse> existing = suggestService.pendingInitialSuggestion(id);
            if (existing.isPresent()) {
                send(emitter, "reasoning", existing.get().reasoning());
                send(emitter, "suggestion", existing.get());
                emitter.complete();
                return;
            }
            SchedulingContext context = suggestService.requireSuggestable(id, TriggerReason.INITIAL);
            List<Recommendation> chosen;
            if ("ai".equals(registry.activeName())) {
                chosen = streamModel(context, emitter);
            } else {
                chosen = rules.recommend(context);
                if (!chosen.isEmpty()) {
                    send(emitter, "reasoning", chosen.get(0).reasoning());
                }
            }
            if (chosen.isEmpty()) {
                send(emitter, "reasoning", "No open slot is available right now.");
                emitter.complete();
                return;
            }
            AppointmentResponse saved = writer.write(id, TriggerReason.INITIAL, chosen);
            send(emitter, "suggestion", saved.suggestion());
            emitter.complete();
        } catch (Exception exception) {
            log.error("Suggest stream failed for appointment {}", id, exception);
            emitter.completeWithError(exception);
        }
    }

    /** Streams Gemini text and falls back to the rule list when the model fails. */
    private List<Recommendation> streamModel(SchedulingContext context, SseEmitter emitter) throws IOException {
        StringBuilder accumulated = new StringBuilder();
        try {
            gateway.stream(prompts.forContext(context), chunk -> {
                accumulated.append(chunk);
                try {
                    send(emitter, "reasoning", chunk);
                } catch (IOException exception) {
                    throw new LlmException("Could not stream reasoning", exception);
                }
            });
            Recommendation pick = parser.parse(accumulated.toString(), context);
            log.info("Gemini selected slot {} for appointment {} confidence {}",
                    pick.slotId(), context.appointment().getId(), pick.confidence());
            return AiSchedulingStrategy.withPickFirst(pick, rules.recommend(context));
        } catch (RuntimeException exception) {
            log.error("Gemini stream failed for appointment {}. Using the rule list.",
                    context.appointment().getId(), exception);
            List<Recommendation> fallback = rules.recommend(context);
            if (!fallback.isEmpty()) {
                send(emitter, "reasoning", fallback.get(0).reasoning());
            }
            return fallback;
        }
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }
}
