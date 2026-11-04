package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "slots")
public class Slot {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    @Version
    private Long version;

    protected Slot() {
    }

    public Slot(String id, Provider provider, Instant startTime, int durationMinutes, SlotStatus status) {
        this.id = id;
        this.provider = provider;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }

    /** Marks an open slot as booked. */
    public void book() {
        if (status != SlotStatus.OPEN) {
            throw new DomainException("SLOT_NOT_OPEN", "Slot " + id + " is not open");
        }
        status = SlotStatus.BOOKED;
    }

    /** Returns a booked slot to open so another appointment can take it. */
    public void release() {
        if (status == SlotStatus.BOOKED) {
            status = SlotStatus.OPEN;
        }
    }

    /** Marks the slot cancelled so it can no longer be booked. */
    public void cancel() {
        status = SlotStatus.CANCELLED;
    }

    /** Returns the slot to open. */
    public void reopen() {
        status = SlotStatus.OPEN;
    }

    public String getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
