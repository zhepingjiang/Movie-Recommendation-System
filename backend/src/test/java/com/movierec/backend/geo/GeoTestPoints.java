package com.movierec.backend.geo;

/**
 * Test helper: the point a given distance and bearing away from a start point (spherical Earth,
 * same radius as {@link GeoDistance}).
 */
public final class GeoTestPoints {

    private GeoTestPoints() {}

    /**
     * Destination point along a great circle.
     *
     * @param lat start latitude in degrees
     * @param lng start longitude in degrees
     * @param distanceKm distance to travel in kilometres
     * @param bearingDegrees direction to travel, clockwise from north
     * @return {latitude, longitude} of the destination, in degrees
     */
    public static double[] destination(double lat, double lng, double distanceKm, double bearingDegrees) {
        double angularDistance = distanceKm / GeoDistance.EARTH_RADIUS_KM;
        double bearing = Math.toRadians(bearingDegrees);
        double startLat = Math.toRadians(lat);
        double startLng = Math.toRadians(lng);
        double destinationLat = Math.asin(Math.sin(startLat) * Math.cos(angularDistance)
                + Math.cos(startLat) * Math.sin(angularDistance) * Math.cos(bearing));
        double destinationLng = startLng + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(startLat),
                Math.cos(angularDistance) - Math.sin(startLat) * Math.sin(destinationLat));
        return new double[] {Math.toDegrees(destinationLat), Math.toDegrees(destinationLng)};
    }
}
