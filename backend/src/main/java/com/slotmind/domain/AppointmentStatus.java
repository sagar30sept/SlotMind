package com.slotmind.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    CONFIRMED,
    RESCHEDULE_PENDING,
    RESCHEDULED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** Statuses that still hold the patient's time on the calendar. */
    public static final Set<AppointmentStatus> ACTIVE =
            EnumSet.of(CONFIRMED, RESCHEDULE_PENDING, RESCHEDULED, CHECKED_IN);

    /** Statuses of a booked visit that has not started and can be moved. */
    public static final Set<AppointmentStatus> MOVABLE = EnumSet.of(CONFIRMED, RESCHEDULED);
}
