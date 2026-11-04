package com.slotmind.scheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SpokenReason {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault());

    private SpokenReason() {
    }

    /** Builds a sentence the desk can read aloud, using the patient, time, and doctor. */
    public static String sentence(String patientName, Instant start, String providerName) {
        return "We can move " + patientName + " to " + CLOCK.format(start) + " with " + providerName + ".";
    }

    /** Returns true when the reasoning is blank or contains ids or status words. */
    public static boolean hasJargon(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return true;
        }
        String upper = reasoning.toUpperCase();
        return upper.contains("SLT-")
                || upper.contains("PRV-")
                || upper.contains("APT-")
                || upper.contains("OFFLINE")
                || upper.contains("PENDING")
                || upper.contains("BOOKED")
                || upper.contains("CANCELLED");
    }
}
