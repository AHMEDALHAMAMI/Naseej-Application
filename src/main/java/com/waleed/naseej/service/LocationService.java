package com.waleed.naseej.service;

import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.repository.NeighborhoodRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Distances on the map and finding the neighborhood a point belongs to. */
@Service
@RequiredArgsConstructor
public class LocationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // A point farther than this from every neighborhood center isn't matched to any neighborhood
    private static final double MAX_NEIGHBORHOOD_DISTANCE_KM = 8.0;

    private final NeighborhoodRepository neighborhoodRepository;

    /** Great-circle distance in km between two points. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Rounded to 2 decimals for responses, e.g. 1.37 km. */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** The idea's own spot, or its neighborhood center when the idea has none. Null when neither is known. */
    public static double[] pointOf(Idea idea) {
        if (idea.getLatitude() != null && idea.getLongitude() != null) {
            return new double[]{idea.getLatitude(), idea.getLongitude()};
        }
        Neighborhood n = idea.getNeighborhood();
        if (n != null && n.getLatitude() != null && n.getLongitude() != null) {
            return new double[]{n.getLatitude(), n.getLongitude()};
        }
        return null;
    }

    /**
     * Which side of its neighborhood the idea is on, e.g. "North side, 0.8 km from the center".
     * Shown to investors instead of a street name, which the platform doesn't store.
     */
    public static String sideOf(Idea idea) {
        Neighborhood n = idea.getNeighborhood();
        if (idea.getLatitude() == null || idea.getLongitude() == null
                || n == null || n.getLatitude() == null || n.getLongitude() == null) {
            return "Neighborhood center";
        }
        double km = distanceKm(n.getLatitude(), n.getLongitude(), idea.getLatitude(), idea.getLongitude());
        if (km < 0.3) {
            return "Neighborhood center";
        }
        double dLng = Math.toRadians(idea.getLongitude() - n.getLongitude());
        double lat1 = Math.toRadians(n.getLatitude());
        double lat2 = Math.toRadians(idea.getLatitude());
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        double bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
        String[] sides = {"North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west"};
        String side = sides[(int) Math.round(bearing / 45.0) % 8];
        return side + " side, " + round2(km) + " km from the center";
    }

    /** The closest neighborhood with coordinates, if one is near enough. */
    public Optional<Neighborhood> findNearestNeighborhood(double lat, double lng) {
        return neighborhoodRepository.findAll().stream()
                .filter(n -> n.getLatitude() != null && n.getLongitude() != null)
                .min(Comparator.comparingDouble(n -> distanceKm(lat, lng, n.getLatitude(), n.getLongitude())))
                .filter(n -> distanceKm(lat, lng, n.getLatitude(), n.getLongitude()) <= MAX_NEIGHBORHOOD_DISTANCE_KM);
    }
}
