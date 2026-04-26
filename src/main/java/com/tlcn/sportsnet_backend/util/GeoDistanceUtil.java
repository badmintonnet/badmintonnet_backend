package com.tlcn.sportsnet_backend.util;

public final class GeoDistanceUtil {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtil() {
    }

    public static double calculateDistanceKm(double fromLat, double fromLon, double toLat, double toLon) {
        double latDistance = Math.toRadians(toLat - fromLat);
        double lonDistance = Math.toRadians(toLon - fromLon);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

