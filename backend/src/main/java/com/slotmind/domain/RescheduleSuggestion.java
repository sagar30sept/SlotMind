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

import java.time.Instant;

@Entity
@Table(name = "reschedule_suggestion")
public class RescheduleSuggestion {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_slot_id", nullable = false)
    private Slot recommendedSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternate_provider_id")
    private Provider alternateProvider;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, length = 1000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false)
    private TriggerReason triggerReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RescheduleSuggestion() {
    }

    /** Creates a pending suggestion. The alternate provider must own the recommended slot. */
    public RescheduleSuggestion(
            String id,
            Appointment appointment,
            Slot recommendedSlot,
            Provider alternateProvider,
            double confidence,
            String reasoning,
            TriggerReason triggerReason) {
        if (alternateProvider != null && !alternateProvider.getId().equals(recommendedSlot.getProvider().getId())) {
            throw new DomainException(
                    "PROVIDER_MISMATCH",
                    "Alternate provider must be the provider of the recommended slot");
        }
        this.id = id;
        this.appointment = appointment;
        this.recommendedSlot = recommendedSlot;
        this.alternateProvider = alternateProvider;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.status = SuggestionStatus.PENDING;
        this.triggerReason = triggerReason;
        this.createdAt = Instant.now();
    }

    /** Accepts a pending suggestion. A second accept is rejected. */
    public void accept() {
        if (status != SuggestionStatus.PENDING) {
            throw new DomainException("SUGGESTION_NOT_PENDING", "Suggestion " + id + " is already " + status);
        }
        status = SuggestionStatus.ACCEPTED;
    }

    /** Rejects a pending suggestion so the desk can ask for another slot. */
    public void reject() {
        if (status != SuggestionStatus.PENDING) {
            throw new DomainException("SUGGESTION_NOT_PENDING", "Suggestion " + id + " is already " + status);
        }
        status = SuggestionStatus.REJECTED;
    }

    public String getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Slot getRecommendedSlot() {
        return recommendedSlot;
    }

    public Provider getAlternateProvider() {
        return alternateProvider;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public TriggerReason getTriggerReason() {
        return triggerReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
