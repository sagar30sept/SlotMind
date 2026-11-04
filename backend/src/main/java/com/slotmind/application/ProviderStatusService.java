package com.slotmind.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.api.ProviderResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Ids;
import com.slotmind.domain.JobType;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import com.slotmind.persistence.AppointmentRepository;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.ProviderRepository;
import com.slotmind.persistence.SuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderStatusService {

    private static final Logger log = LoggerFactory.getLogger(ProviderStatusService.class);

    private final ProviderRepository providers;
    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;
    private final RescheduleOrchestrator orchestrator;
    private final OutboxJobRepository jobs;
    private final AppointmentViews views;
    private final ObjectMapper objectMapper;

    public ProviderStatusService(
            ProviderRepository providers,
            AppointmentRepository appointments,
            SuggestionRepository suggestions,
            RescheduleOrchestrator orchestrator,
            OutboxJobRepository jobs,
            AppointmentViews views,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.orchestrator = orchestrator;
        this.jobs = jobs;
        this.views = views;
        this.objectMapper = objectMapper;
    }

    /**
     * Changes a provider's status. Going offline enqueues one fan-out job for their confirmed appointments.
     * Coming back to available enqueues a new suggestion for visits still waiting with no proposal.
     */
    @Transactional
    public ProviderResponse update(String providerId, ProviderStatus status) {
        Provider provider = providers.findById(providerId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Provider " + providerId + " was not found"));
        if (provider.getStatus() == status) {
            log.debug("Provider {} is already {}", providerId, status);
            return views.toProvider(provider);
        }
        ProviderStatus previous = provider.getStatus();
        provider.changeStatus(status);
        providers.saveAndFlush(provider);
        log.info("Provider {} status changed from {} to {}", providerId, previous, status);
        if (status == ProviderStatus.OFFLINE) {
            String dedupeKey = "FANOUT:OFFLINE:" + provider.getId() + ":v" + provider.getVersion();
            if (!jobs.existsByDedupeKey(dedupeKey)) {
                jobs.save(new OutboxJob(
                        Ids.next("JOB-"),
                        JobType.FANOUT_PROVIDER_OFFLINE,
                        dedupeKey,
                        json(provider.getId())));
                log.info("Enqueued offline fan-out for provider {}", providerId);
            } else {
                log.debug("Offline fan-out for provider {} already exists", providerId);
            }
        }
        if (status == ProviderStatus.AVAILABLE) {
            retryWaitingVisits(provider);
        }
        return views.toProvider(provider);
    }

    /** Asks again for this provider's waiting visits that never received a suggestion. */
    private void retryWaitingVisits(Provider provider) {
        String sourceId = "AVAILABLE:" + provider.getId() + ":v" + provider.getVersion();
        for (Appointment appointment : appointments.findByProviderAndStatus(provider.getId(), AppointmentStatus.RESCHEDULE_PENDING)) {
            if (!suggestions.findByAppointmentAndStatus(appointment.getId(), SuggestionStatus.PENDING).isEmpty()) {
                log.debug("Appointment {} already has a pending suggestion", appointment.getId());
                continue;
            }
            orchestrator.enqueueWaiting(appointment, TriggerReason.PROVIDER_OFFLINE, sourceId);
        }
    }

    private String json(String providerId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("providerId", providerId));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write fan-out job", exception);
        }
    }
}
