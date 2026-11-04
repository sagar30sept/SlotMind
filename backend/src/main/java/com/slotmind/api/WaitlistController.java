package com.slotmind.api;

import com.slotmind.application.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WaitlistController {

    private final WaitlistService waitlist;

    public WaitlistController(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    /** Lists patients waiting for an opening. */
    @GetMapping("/waitlist")
    public List<WaitlistResponse> list() {
        return waitlist.list();
    }

    /** Adds a patient to the waitlist. */
    @PostMapping("/waitlist")
    public ResponseEntity<WaitlistResponse> add(@Valid @RequestBody AddWaitlistRequest request) {
        return ResponseEntity.status(201).body(waitlist.add(request));
    }

    /** Removes a patient from the waitlist. */
    @DeleteMapping("/waitlist/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        waitlist.remove(id);
        return ResponseEntity.noContent().build();
    }
}
