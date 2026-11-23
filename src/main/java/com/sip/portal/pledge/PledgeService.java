package com.sip.portal.pledge;

import com.sip.portal.common.ApiException;
import com.sip.portal.notification.Notifier;
import com.sip.portal.problem.*;
import com.sip.portal.project.Proposal;
import com.sip.portal.project.ProposalStatus;
import com.sip.portal.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Industry offers and the university's answers. Every change locks the problem first. */
@Service
public class PledgeService {
    record OpenProposal(ProblemResponse problem, String summary, int durationWeeks, Funding funding,
            boolean hasPendingPledge) {}
    record MyPledge(String problemId, String problemTitle, PledgeResponse pledge) {}

    private final ProblemRepository problems;
    private final PledgeRepository pledges;

    private final Notifier notifier;

    PledgeService(ProblemRepository problems, PledgeRepository pledges, Notifier notifier) {
        this.notifier = notifier;
        this.problems = problems;
        this.pledges = pledges;
    }

    // ---- industry ----

    @Transactional(readOnly = true)
    public List<OpenProposal> openProposals(User user) {
        return problems.findOpenForSupport().stream().map(p -> {
            Proposal proposal = p.getAssignment().getProposal();
            boolean pending = p.getPledges().stream().anyMatch(x -> x.isPending() && x.isFrom(user.getOrganization()));
            return new OpenProposal(ProblemResponse.of(p), proposal.getSummary(), proposal.getDurationWeeks(),
                    Funding.of(p), pending);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<MyPledge> mine(User user) {
        return pledges.findByOrganizationIdOrderByIdDesc(user.getOrganization().getId()).stream()
                .map(p -> new MyPledge(p.getProblem().getCaseId(), p.getProblem().getTitle(), PledgeResponse.of(p)))
                .toList();
    }

    @Transactional
    public void offer(String caseId, PledgeRequests.Offer request, User user) {
        Problem problem = locked(caseId);
        requireOpen(problem);
        if (problem.getPledges().stream().anyMatch(p -> p.isPending() && p.isFrom(user.getOrganization()))) {
            throw conflict("Your organisation already has an offer waiting for an answer.");
        }
        boolean isFunding = request.type() == PledgeType.FUNDING;
        if (isFunding && request.amount() == null) throw bad("Please enter the amount.");
        if (isFunding) requireWithinBudget(problem, request.amount());
        problem.getPledges().add(new Pledge(problem, user.getOrganization(), request.type(),
                isFunding ? request.amount() : null, request.description().strip(), Instant.now()));
        university(problem, user.getOrganization().getName() + " offered support: " + problem.getTitle());
    }

    @Transactional
    public void withdraw(String caseId, long pledgeId, User user) {
        Pledge pledge = find(locked(caseId), pledgeId);
        if (!pledge.isFrom(user.getOrganization())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This offer belongs to another organisation.");
        }
        requirePending(pledge);
        pledge.close(PledgeStatus.WITHDRAWN, null, Instant.now());
        university(pledge.getProblem(), user.getOrganization().getName() + " withdrew its offer: "
                + pledge.getProblem().getTitle());
    }

    // ---- university ----

    @Transactional
    public void accept(String caseId, long pledgeId, User user) {
        Problem problem = assignedTo(caseId, user);
        Pledge pledge = find(problem, pledgeId);
        requirePending(pledge);
        requireOpen(problem);
        if (pledge.getType() == PledgeType.FUNDING) requireWithinBudget(problem, pledge.getAmountInr());
        Instant now = Instant.now();
        pledge.close(PledgeStatus.ACCEPTED, null, now);
        notifier.organization(pledge.getOrganization(), problem, "Your offer was accepted: " + problem.getTitle());
        problem.record(user.getOrganization().getName(), "Support accepted from " + pledge.getOrganization().getName()
                + " (" + pledge.getType().name().toLowerCase(Locale.ROOT) + ").", now);
    }

    @Transactional
    public void decline(String caseId, long pledgeId, String reason, User user) {
        Pledge pledge = find(assignedTo(caseId, user), pledgeId);
        requirePending(pledge);
        pledge.close(PledgeStatus.DECLINED, reason.strip(), Instant.now());
        notifier.organization(pledge.getOrganization(), pledge.getProblem(),
                "Your offer was declined: " + pledge.getProblem().getTitle());
    }

    // ---- rules ----

    private void university(Problem problem, String message) {
        notifier.organization(problem.getAssignment().getOrganization(), problem, message);
    }

    private Problem locked(String caseId) {
        return problems.findForUpdate(caseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This problem does not exist."));
    }

    private Problem assignedTo(String caseId, User user) {
        Problem problem = locked(caseId);
        Assignment a = problem.getAssignment();
        if (a == null || user.getOrganization() == null
                || !a.getOrganization().getId().equals(user.getOrganization().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This problem is not assigned to your university.");
        }
        return problem;
    }

    private static Pledge find(Problem problem, long pledgeId) {
        return problem.getPledges().stream().filter(p -> p.getId() == pledgeId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This offer does not exist."));
    }

    private static void requireOpen(Problem problem) {
        Assignment a = problem.getAssignment();
        boolean open = problem.getStatus() == ProblemStatus.IN_PROGRESS && a.getProposal() != null
                && a.getProposal().getStatus() == ProposalStatus.APPROVED && a.getCompletionRequestedAt() == null;
        if (!open) throw conflict("This project is not open for support.");
    }

    private static void requirePending(Pledge pledge) {
        if (!pledge.isPending()) throw conflict("This offer has already been answered or withdrawn.");
    }

    private static void requireWithinBudget(Problem problem, long amount) {
        long remaining = Funding.of(problem).remaining();
        if (amount > remaining) throw conflict("Only ₹" + remaining + " of funding is still needed.");
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
