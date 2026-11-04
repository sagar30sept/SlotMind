package com.slotmind.api;

import com.slotmind.application.DeskQueryService;
import com.slotmind.application.ProviderStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProviderController {

    private final DeskQueryService desk;
    private final ProviderStatusService status;

    public ProviderController(DeskQueryService desk, ProviderStatusService status) {
        this.desk = desk;
        this.status = status;
    }

    /** Lists every provider and their current status. */
    @GetMapping("/providers")
    public List<ProviderResponse> list() {
        return desk.providers();
    }

    /** Changes a provider's status. Going offline starts reschedule work in the background. */
    @PatchMapping("/providers/{id}/status")
    public ProviderResponse update(@PathVariable String id, @Valid @RequestBody ProviderStatusRequest request) {
        return status.update(id, request.status());
    }
}
