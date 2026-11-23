package com.sip.portal.problem;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** How long a problem may wait in each stage before it is flagged. Finished stages have no limit. */
public final class StageLimits {
    public static final Map<ProblemStatus, Duration> LIMITS = Map.of(
            ProblemStatus.SUBMITTED, Duration.ofDays(2), // the form promises a review within 48 hours
            ProblemStatus.VALIDATED, Duration.ofDays(7),
            ProblemStatus.ASSIGNED, Duration.ofDays(7),
            ProblemStatus.IN_PROGRESS, Duration.ofDays(180));

    private StageLimits() {}

    public static boolean isOverdue(Problem problem, Instant now) {
        Duration limit = LIMITS.get(problem.getStatus());
        return limit != null && problem.getStatusSince().plus(limit).isBefore(now);
    }
}
