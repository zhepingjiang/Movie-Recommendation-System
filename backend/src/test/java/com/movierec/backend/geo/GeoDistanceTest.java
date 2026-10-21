package com.movierec.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    private static final double UNION_STATION_LAT = 43.6453;
    private static final double UNION_STATION_LNG = -79.3806;

    @Test
    void samePointIsZeroKilometresAway() {
        assertThat(GeoDistance.haversineKm(UNION_STATION_LAT, UNION_STATION_LNG, UNION_STATION_LAT, UNION_STATION_LNG))
                .isZero();
    }

    @Test
    void torontoToOttawaMatchesKnownDistance() {
        // Union Station to Ottawa's Parliament Hill: ~352 km great-circle.
        double distanceKm = GeoDistance.haversineKm(UNION_STATION_LAT, UNION_STATION_LNG, 45.4236, -75.7009);

        assertThat(distanceKm).isCloseTo(352, within(2.0));
    }

    @Test
    void roundTripsWithDestinationHelper() {
        double[] point = GeoTestPoints.destination(UNION_STATION_LAT, UNION_STATION_LNG, 12.5, 73);

        assertThat(GeoDistance.haversineKm(UNION_STATION_LAT, UNION_STATION_LNG, point[0], point[1]))
                .isCloseTo(12.5, within(1e-6));
    }
}
