package com.example.security.domain.detection;

/**
 * Geographic point resolved from an IP. Pure value.
 */
public record GeoPoint(String country, double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Great-circle distance to another point in kilometres. */
    public double distanceKm(GeoPoint other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
