package com.slotmind.api;

import com.slotmind.application.DeskQueryService;
import com.slotmind.application.SlotStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SlotController {

    private final DeskQueryService desk;
    private final SlotStatusService status;

    public SlotController(DeskQueryService desk, SlotStatusService status) {
        this.desk = desk;
        this.status = status;
    }

    /** Lists every slot with its provider. */
    @GetMapping("/slots")
    public List<SlotResponse> list() {
        return desk.slots();
    }

    /** Changes a slot's status. Cancelling it starts reschedule work in the background. */
    @PatchMapping("/slots/{id}/status")
    public SlotResponse update(@PathVariable String id, @Valid @RequestBody SlotStatusRequest request) {
        return status.update(id, request.status());
    }
}
