package com.slotmind.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.api.ProviderResponse;
import com.slotmind.domain.Ids;
import com.slotmind.domain.JobType;
import com.slotmind.domain.NotFoundException;
import com.slotmind.domain.OutboxJob;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.persistence.OutboxJobRepository;
import com.slotmind.persistence.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderStatusService {

    private static final Logger log = LoggerFactory.getLogger(ProviderStatusService.class);

    private final ProviderRepository providers;
    private final OutboxJobRepository jobs;
    private final AppointmentViews views;
    private final ObjectMapper objectMapper;

    public ProviderStatusService(
            ProviderRepository providers,
            OutboxJobRepository jobs,
            AppointmentViews views,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.jobs = jobs;
        this.views = views;
        this.objectMapper = objectMapper;
    }

    /**
     * Changes a provider's status. Going offline enqueues one fan-out job for their confirmed appointments.
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
        return views.toProvider(provider);
    }

    private String json(String providerId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("providerId", providerId));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write fan-out job", exception);
        }
    }
}
