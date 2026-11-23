package com.sip.portal.problem;

import java.util.Map;

/** Location checks. Mirrors frontend/src/utils/jharkhand.js; this copy is the one that is enforced. */
final class Jharkhand {
    private record Point(double lat, double lng) {}

    private static final double MAX_DISTRICT_RADIUS_KM = 90;

    /** District headquarters (approximate). */
    private static final Map<String, Point> DISTRICTS = Map.ofEntries(
            at("Bokaro", 23.6693, 86.1511), at("Chatra", 24.206, 84.87), at("Deoghar", 24.4852, 86.6948),
            at("Dhanbad", 23.7957, 86.4304), at("Dumka", 24.2676, 87.2497), at("East Singhbhum", 22.8046, 86.2029),
            at("Garhwa", 24.16, 83.807), at("Giridih", 24.1913, 86.3096), at("Godda", 24.827, 87.213),
            at("Gumla", 23.044, 84.542), at("Hazaribagh", 23.9925, 85.3637), at("Jamtara", 23.963, 86.802),
            at("Khunti", 23.072, 85.279), at("Koderma", 24.468, 85.594), at("Latehar", 23.744, 84.5),
            at("Lohardaga", 23.433, 84.683), at("Pakur", 24.634, 87.849), at("Palamu", 24.04, 84.07),
            at("Ramgarh", 23.63, 85.514), at("Ranchi", 23.3441, 85.3096), at("Sahibganj", 25.238, 87.636),
            at("Seraikela-Kharsawan", 22.7, 85.93), at("Simdega", 22.615, 84.502),
            at("West Singhbhum", 22.554, 85.806));

    private Jharkhand() {}

    static boolean isDistrict(String name) {
        return DISTRICTS.containsKey(name);
    }

    static boolean isInArea(double lat, double lng) {
        return lat >= 21.95 && lat <= 25.35 && lng >= 83.3 && lng <= 87.98;
    }

    static boolean isNearDistrict(double lat, double lng, String district) {
        Point hq = DISTRICTS.get(district);
        return distanceKm(lat, lng, hq.lat, hq.lng) <= MAX_DISTRICT_RADIUS_KM;
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double h = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
        return 2 * 6371 * Math.asin(Math.sqrt(h));
    }

    private static Map.Entry<String, Point> at(String name, double lat, double lng) {
        return Map.entry(name, new Point(lat, lng));
    }
}
