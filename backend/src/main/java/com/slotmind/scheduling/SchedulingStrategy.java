package com.slotmind.scheduling;

import java.util.List;

public interface SchedulingStrategy {

    /** Config key used to select this ranking. */
    String name();

    /** Returns ranked slot recommendations. Does not load the database. */
    List<Recommendation> recommend(SchedulingContext context);
}
