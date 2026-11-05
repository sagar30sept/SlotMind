package com.slotmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** The clock lifecycle rules read "now" from; tests can replace it. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
