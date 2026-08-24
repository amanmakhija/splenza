package com.splitwise.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a single application-wide {@link Clock}. Date/time-sensitive services
 * (recurring-payment execution and detection) inject this instead of calling
 * {@code LocalDate.now()} directly, so tests can pin "today" to a fixed instant
 * and assert deterministic scheduling behaviour.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
