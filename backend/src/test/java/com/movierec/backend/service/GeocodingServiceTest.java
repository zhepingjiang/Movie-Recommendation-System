package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GeocodingService#normalizeQuery}, which decides the cache key for a typed
 * address.
 */
class GeocodingServiceTest {

    @Test
    void collapsesEveryKindOfWhitespaceAndLowerCases() {
        assertThat(GeocodingService.normalizeQuery("  Union\tStation,\n  TORONTO  "))
                .isEqualTo("union station, toronto");
    }

    @Test
    void differentlySpacedAddressesShareOneCacheKey() {
        assertThat(GeocodingService.normalizeQuery("Union\t\tStation Toronto"))
                .isEqualTo(GeocodingService.normalizeQuery("union station   toronto"));
    }
}
