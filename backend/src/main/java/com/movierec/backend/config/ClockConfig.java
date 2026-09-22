package com.movierec.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the system clock as a bean so time-dependent services (e.g. nearline freshness decay in
 * {@code PersonalizedRecommendationService}) can be tested against a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
