package com.slotmind.api;

import com.slotmind.application.SuggestionDecisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestionController {

    private final SuggestionDecisionService decisions;

    public SuggestionController(SuggestionDecisionService decisions) {
        this.decisions = decisions;
    }

    /** Accepts or rejects a pending suggestion. */
    @PatchMapping("/suggestions/{id}")
    public AppointmentResponse decide(@PathVariable String id, @Valid @RequestBody SuggestionActionRequest request) {
        return decisions.decide(id, request.action());
    }
}
