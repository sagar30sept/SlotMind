package com.slotmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotmind")
public record SlotMindProperties(Scheduling scheduling, Llm llm, Worker worker) {

    public record Scheduling(String strategy) {
    }

    public record Llm(String provider, String model, String apiKey) {
    }

    public record Worker(long pollMs, long initialDelayMs, long leaseSeconds, int batchSize) {
    }
}
