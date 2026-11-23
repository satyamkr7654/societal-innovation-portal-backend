package com.sip.portal.admin;

import com.sip.portal.problem.ProblemRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Reported and solved problems for each of the last 12 months, in Indian time. */
final class MonthlyTrend {
    record Month(String month, long reported, long solved) {}

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private MonthlyTrend() {}

    static List<Month> of(ProblemRepository problems, Instant now) {
        YearMonth current = YearMonth.from(now.atZone(INDIA));
        YearMonth first = current.minusMonths(11);
        Instant from = first.atDay(1).atStartOfDay(INDIA).toInstant();
        Map<YearMonth, Long> reported = count(problems.findSubmittedSince(from).stream());
        Map<YearMonth, Long> solved = count(problems.findSolvedTimes().stream().map(t -> t.getSolvedAt()));
        return IntStream.range(0, 12).mapToObj(first::plusMonths)
                .map(m -> new Month(m.toString(), reported.getOrDefault(m, 0L), solved.getOrDefault(m, 0L))).toList();
    }

    private static Map<YearMonth, Long> count(Stream<Instant> times) {
        return times.map(t -> YearMonth.from(t.atZone(INDIA)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
