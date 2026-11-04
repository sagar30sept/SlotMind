package com.slotmind.api;

import jakarta.validation.constraints.NotNull;

public record SuggestionActionRequest(@NotNull SuggestionAction action) {
}
