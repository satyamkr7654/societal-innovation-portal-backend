package com.sip.portal.admin;

import com.sip.portal.common.ApiException;
import com.sip.portal.notification.Notifier;
import com.sip.portal.problem.*;
import com.sip.portal.user.*;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin decisions on problems. Each runs only from the one status it is allowed in. */
@Service
public class ReviewService {
    private final ProblemRepository problems;
    private final OrganizationRepository organizations;

    private final Notifier notifier;

    ReviewService(ProblemRepository problems, OrganizationRepository organizations, Notifier notifier) {
        this.notifier = notifier;
        this.problems = problems;
        this.organizations = organizations;
    }

    @Transactional
    public ProblemResponse validate(String caseId, String remarks, User admin) {
        Problem problem = require(caseId, ProblemStatus.SUBMITTED);
        notifier.citizen(problem, "Your problem was verified: " + problem.getTitle());
        return move(problem, ProblemStatus.VALIDATED, orDefault(remarks, "Verified as genuine and eligible."), admin);
    }

    @Transactional
    public ProblemResponse reject(String caseId, String reason, User admin) {
        Problem problem = require(caseId, ProblemStatus.SUBMITTED);
        problem.setRejectionReason(reason.strip());
        notifier.citizen(problem, "Your problem was rejected: " + problem.getTitle());
        return move(problem, ProblemStatus.REJECTED, reason.strip(), admin);
    }

    @Transactional
    public ProblemResponse assign(String caseId, long organizationId, String remarks, User admin) {
        Problem problem = require(caseId, ProblemStatus.VALIDATED);
        Organization university = organizations.findAssignableUniversity(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Please choose an approved university."));
        problem.setAssignment(new Assignment(problem, university, null, null, null));
        notifier.citizen(problem, "Your problem was assigned to " + university.getName() + ".");
        notifier.organization(university, problem, "New assignment: " + problem.getTitle());
        return move(problem, ProblemStatus.ASSIGNED, orDefault(remarks, "Assigned to " + university.getName() + "."),
                admin);
    }

    private Problem require(String caseId, ProblemStatus expected) {
        Problem problem = problems.findForUpdate(caseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This problem does not exist."));
        if (problem.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This problem's status has changed. Please reload the page.");
        }
        return problem;
    }

    private ProblemResponse move(Problem problem, ProblemStatus next, String remarks, User admin) {
        problem.moveTo(next, admin.getName() + " (Govt. Admin)", remarks, Instant.now());
        return ProblemResponse.of(problem);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
