package com.sip.portal.problem;

import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The only data visible without signing in. Reports appear once an admin has verified them,
 * without names, descriptions, addresses or photos, and with the location rounded to about 1 km.
 */
@RestController
@RequestMapping("/api/public")
class PublicController {
    record PublicProblem(String id, String title, String category, String status, String district, double lat,
            double lng) {}

    private final ProblemRepository problems;

    PublicController(ProblemRepository problems) {
        this.problems = problems;
    }

    @GetMapping("/problems")
    ResponseEntity<List<PublicProblem>> problems() {
        List<PublicProblem> list = problems.findVerified().stream().map(p -> new PublicProblem(p.caseId(), p.title(),
                p.category().label(), p.status().name(), p.district(), rounded(p.latitude()), rounded(p.longitude())))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic()).body(list);
    }

    private static double rounded(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
