package com.slotmind.domain;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    /** Builds a short prefixed id from a random suffix. */
    public static String next(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
