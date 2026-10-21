package com.movierec.backend.geo;

import com.uber.h3core.AreaUnit;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The one place H3 cells are computed, so every writer (the Theater entity hook, the V16 seed
 * migration) and the nearby search agree on the resolution and the cell for a coordinate.
 *
 * <p>Resolution 7: average hexagon edge ~1.41 km, so cells are small enough that a 2 km search
 * only scans a handful of them, yet a 50 km search stays at a few thousand cell ids in one
 * {@code IN} list.
 */
public final class H3Cells {

    /** Resolution stored in {@code theaters.h3_cell}. Changing it requires recomputing every row. */
    public static final int RESOLUTION = 7;

    private static final H3Core H3 = loadH3();

    private H3Cells() {}

    private static H3Core loadH3() {
        try {
            return H3Core.newInstance();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the H3 native library", e);
        }
    }

    /**
     * Returns the resolution-{@value #RESOLUTION} cell containing a coordinate.
     *
     * @param lat latitude in degrees
     * @param lng longitude in degrees
     * @return the H3 cell id
     */
    public static long cellFor(double lat, double lng) {
        return H3.latLngToCell(lat, lng, RESOLUTION);
    }

    /**
     * Returns every cell that could contain a point within {@code radiusKm} of the given
     * coordinate. A superset: callers must still filter by exact distance.
     *
     * @param lat latitude of the search centre in degrees
     * @param lng longitude of the search centre in degrees
     * @param radiusKm search radius in kilometres
     * @return the candidate cell ids, including the centre's own cell
     */
    public static List<Long> cellsWithinRadius(double lat, double lng, double radiusKm) {
        long centerCell = cellFor(lat, lng);
        return H3.gridDisk(centerCell, ringsToCover(radiusKm, localEdgeLengthKm(centerCell)));
    }

    /**
     * Edge length of the hexagon around the search centre, derived from its exact area. H3 cell
     * size varies with location (by up to ~2x across the globe), so this is more accurate than
     * the global average; over a 50 km search it changes by far less than one ring.
     *
     * @param cell the H3 cell to measure
     * @return the edge length, in kilometres, of a regular hexagon with that cell's area
     */
    static double localEdgeLengthKm(long cell) {
        double areaKm2 = H3.cellArea(cell, AreaUnit.km2);
        // Regular hexagon: area = (3 * sqrt(3) / 2) * edge^2.
        return Math.sqrt(2 * areaKm2 / (3 * Math.sqrt(3)));
    }

    /**
     * Number of grid rings ({@code k} in gridDisk) needed so the disk covers a circle.
     *
     * <p>Math, for a hexagonal grid with edge length {@code e}:
     * <ul>
     *   <li>A point within {@code r} of the user sits in a cell whose centre is at most
     *       {@code e} further away (a hexagon's circumradius equals its edge). The user's own
     *       cell centre is likewise up to {@code e} from the user. So the two cell centres are
     *       at most {@code r + 2e} apart.
     *   <li>Neighbouring centres are {@code sqrt(3) * e} apart. Ring {@code k}'s closest centres
     *       lie along the zigzag between two axes, at {@code k * 1.5e} from the centre cell. So
     *       any centre within distance {@code D} is within {@code ceil(D / 1.5e)} rings.
     *   <li>{@code k = ceil((r + 2e) / 1.5e)}, plus one ring of slack for cell-size distortion
     *       across the disk and floating-point error at the boundary.
     * </ul>
     * Example at resolution 7 ({@code e} ~ 1.4 km): r = 5 km gives k = 5 (91 cells); r = 50 km
     * gives k = 27 (~2,270 cells).
     *
     * @param radiusKm search radius in kilometres
     * @param edgeLengthKm hexagon edge length near the search centre, in kilometres
     * @return the gridDisk {@code k} to use
     */
    static int ringsToCover(double radiusKm, double edgeLengthKm) {
        return (int) Math.ceil((radiusKm + 2 * edgeLengthKm) / (1.5 * edgeLengthKm)) + 1;
    }

    /**
     * Centre coordinate of a cell; used by tests to place points in known cells.
     *
     * @param cell the H3 cell id
     * @return the cell's centre
     */
    static LatLng cellCenter(long cell) {
        return H3.cellToLatLng(cell);
    }
}
