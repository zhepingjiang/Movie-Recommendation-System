package com.movierec.backend.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Excludes the Prometheus scrape endpoint from {@code http.server.requests} so Prometheus
 * scraping itself doesn't show up as traffic in its own dashboards.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter ignorePrometheusScrapeMeterFilter() {
        return MeterFilter.deny(id -> "/actuator/prometheus".equals(id.getTag("uri")));
    }
}
