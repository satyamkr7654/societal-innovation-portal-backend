package com.sip.portal.admin;

import com.sip.portal.problem.*;
import com.sip.portal.user.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {
    record Count(String name, long count) {}

    /** Averages are null when there is nothing to average. */
    record Stats(long total, Map<String, Long> byStatus, List<Count> byDistrict, List<Count> byCategory,
            long fundedProblems, long fundingInr, Double averageRating, Double averageDaysToSolve,
            long pendingAccounts, long proposalsToReview, long completionsToConfirm, long overdue,
            List<MonthlyTrend.Month> monthly) {}

    private final ProblemRepository problems;
    private final UserRepository users;

    StatsService(ProblemRepository problems, UserRepository users) {
        this.problems = problems;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Instant now = Instant.now();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ProblemStatus status : ProblemStatus.values()) byStatus.put(status.name(), 0L);
        problems.countByStatus().forEach(row -> byStatus.put(((ProblemStatus) row[0]).name(), (Long) row[1]));

        List<Count> byDistrict = problems.countByDistrict().stream()
                .map(row -> new Count((String) row[0], (Long) row[1])).toList();
        List<Count> byCategory = problems.countByCategory().stream()
                .map(row -> new Count(((Category) row[0]).label(), (Long) row[1])).toList();

        OptionalDouble days = problems.findSolvedTimes().stream()
                .mapToDouble(t -> Duration.between(t.getSubmittedAt(), t.getSolvedAt()).toSeconds() / 86_400.0)
                .average();

        return new Stats(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus, byDistrict,
                byCategory, problems.countFunded(), problems.totalFundingInr(), problems.averageRating(),
                days.isPresent() ? days.getAsDouble() : null, users.countByAccountStatus(AccountStatus.PENDING),
                problems.countProposalsToReview(), problems.countCompletionsToConfirm(),
                overdue(now), MonthlyTrend.of(problems, now));
    }

    private long overdue(Instant now) {
        return StageLimits.LIMITS.entrySet().stream().mapToLong(
                e -> problems.countByStatusAndStatusSinceBefore(e.getKey(), now.minus(e.getValue()))).sum();
    }
}
