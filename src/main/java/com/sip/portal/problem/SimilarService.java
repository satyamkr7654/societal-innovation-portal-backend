package com.sip.portal.problem;

import com.sip.portal.common.ApiException;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds open problems of the same category close to a point. It warns; it never blocks. */
@Service
public class SimilarService {
    record Similar(String id, String title, String status, long distanceMeters) {}

    private static final double RADIUS_KM = 0.5;
    private static final int MAX_RESULTS = 5;

    private final ProblemRepository problems;

    SimilarService(ProblemRepository problems) {
        this.problems = problems;
    }

    @Transactional(readOnly = true)
    public List<Similar> find(String categoryLabel, double lat, double lng) {
        Category category = Category.fromLabel(categoryLabel)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Please select a civic category."));
        if (!Jharkhand.isInArea(lat, lng)) return List.of(); // also covers NaN
        // A box the database can search quickly, then the exact circle
        double latRange = RADIUS_KM / 111.0;
        double lngRange = RADIUS_KM / (111.0 * Math.cos(Math.toRadians(lat)));
        return problems.findOpenInBox(category, lat - latRange, lat + latRange, lng - lngRange, lng + lngRange).stream()
                .map(p -> new Similar(p.getCaseId(), p.getTitle(), p.getStatus().name(),
                        Math.round(Jharkhand.distanceKm(lat, lng, p.getLatitude(), p.getLongitude()) * 1000)))
                .filter(s -> s.distanceMeters() <= RADIUS_KM * 1000)
                .sorted(Comparator.comparingLong(Similar::distanceMeters)).limit(MAX_RESULTS).toList();
    }
}
