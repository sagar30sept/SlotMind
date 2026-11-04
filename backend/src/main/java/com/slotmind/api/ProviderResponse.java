package com.slotmind.api;

import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Specialty;

public record ProviderResponse(String id, String name, Specialty specialty, ProviderStatus status) {
}
