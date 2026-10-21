package com.movierec.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class H3CellsTest {

    private static final double UNION_STATION_LAT = 43.6453;
    private static final double UNION_STATION_LNG = -79.3806;

    @Test
    void ringsToCoverMatchesTheWorkedExamplesInTheJavadoc() {
        double edgeLengthKm = 1.4;

        // (5 + 2.8) / 2.1 = 3.71 -> 4, +1 slack.
        assertThat(H3Cells.ringsToCover(5, edgeLengthKm)).isEqualTo(5);
        // (50 + 2.8) / 2.1 = 25.1 -> 26, +1 slack.
        assertThat(H3Cells.ringsToCover(50, edgeLengthKm)).isEqualTo(27);
    }

    @Test
    void localEdgeLengthIsCloseToResolutionSevenAverage() {
        double edgeLengthKm = H3Cells.localEdgeLengthKm(H3Cells.cellFor(UNION_STATION_LAT, UNION_STATION_LNG));

        // H3's published average edge at resolution 7 is ~1.41 km; cells vary by location.
        assertThat(edgeLengthKm).isBetween(1.0, 1.8);
    }

    /**
     * The guarantee the search relies on: every point within the radius lies in one of the
     * returned cells. Checked at the exact radius (the worst case) in 360 directions and at a few
     * inner distances, from Toronto, Thunder Bay, Ontario's far north and its southern tip.
     */
    @ParameterizedTest
    @CsvSource({
        "43.6453, -79.3806, 2",
        "43.6453, -79.3806, 5",
        "43.6453, -79.3806, 25",
        "43.6453, -79.3806, 50",
        "48.3809, -89.2477, 10",
        "56.0, -88.0, 50",
        "42.0, -82.6, 0.3",
    })
    void diskContainsEveryPointWithinTheRadius(double centerLat, double centerLng, double radiusKm) {
        Set<Long> candidateCells = new HashSet<>(H3Cells.cellsWithinRadius(centerLat, centerLng, radiusKm));

        for (double fraction : List.of(1.0, 0.999, 0.75, 0.5, 0.1)) {
            for (int bearingDegrees = 0; bearingDegrees < 360; bearingDegrees++) {
                double[] point = GeoTestPoints.destination(centerLat, centerLng, radiusKm * fraction, bearingDegrees);
                assertThat(candidateCells)
                        .as("point %.3f km at bearing %d", radiusKm * fraction, bearingDegrees)
                        .contains(H3Cells.cellFor(point[0], point[1]));
            }
        }
    }

    @Test
    void diskSizeStaysSmallEnoughForOneInQuery() {
        assertThat(H3Cells.cellsWithinRadius(UNION_STATION_LAT, UNION_STATION_LNG, 2)).hasSizeLessThan(100);
        assertThat(H3Cells.cellsWithinRadius(UNION_STATION_LAT, UNION_STATION_LNG, 50)).hasSizeLessThan(3000);
    }
}
