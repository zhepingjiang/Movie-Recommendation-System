package com.movierec.backend.geo;

/**
 * Great-circle distance on a spherical Earth. Accurate to ~0.5% (the Earth is slightly
 * flattened), which is far finer than a theater search needs.
 */
public final class GeoDistance {

    /** Mean Earth radius. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistance() {}

    /**
     * Haversine distance between two coordinates.
     *
     * @param fromLat latitude of the first point in degrees
     * @param fromLng longitude of the first point in degrees
     * @param toLat latitude of the second point in degrees
     * @param toLng longitude of the second point in degrees
     * @return the distance in kilometres
     */
    public static double haversineKm(double fromLat, double fromLng, double toLat, double toLng) {
        double latDelta = Math.toRadians(toLat - fromLat);
        double lngDelta = Math.toRadians(toLng - fromLng);
        double halfChord = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                        * Math.sin(lngDelta / 2) * Math.sin(lngDelta / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(halfChord)));
    }
}
