package com.sip.portal.project;

import com.sip.portal.common.ApiException;
import com.sip.portal.pledge.Pledge;
import com.sip.portal.pledge.PledgeStatus;
import com.sip.portal.problem.*;
import com.sip.portal.user.Role;
import com.sip.portal.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** University work on assigned problems, and the admin decisions about that work. */
@Service
public class ProjectService {
    /** One row of the university's own list. */
    record Summary(ProblemResponse problem, int milestonesDone, int milestonesTotal, String proposalStatus,
            boolean completionRequested, int pendingPledges) {}

    private final ProblemRepository problems;

    ProjectService(ProblemRepository problems) {
        this.problems = problems;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public Optional<ProjectResponse> get(String caseId, User user) {
        Problem problem = problems.findByCaseId(caseId).orElseThrow(ProjectService::notFound);
        Assignment a = problem.getAssignment();
        if (a == null) return Optional.empty();
        boolean mine = isMine(a, user);
        boolean full = mine || user.getRole() == Role.ADMIN;
        boolean industry = user.getRole() == Role.INDUSTRY;
        boolean approved = a.getProposal() != null && a.getProposal().getStatus() == ProposalStatus.APPROVED;
        return Optional.of(ProjectResponse.of(problem, mine, full || (industry && approved),
                pledge -> full || (industry && pledge.isFrom(user.getOrganization()))));
    }

    @Transactional(readOnly = true)
    public List<Summary> mine(User user) {
        return problems.findByAssignedOrganization(user.getOrganization().getId()).stream().map(p -> {
            Assignment a = p.getAssignment();
            return new Summary(ProblemResponse.of(p), (int) a.getMilestones().stream().filter(Milestone::isDone).count(),
                    a.getMilestones().size(), a.getProposal() == null ? null : a.getProposal().getStatus().name(),
                    a.getCompletionRequestedAt() != null,
                    (int) p.getPledges().stream().filter(Pledge::isPending).count());
        }).toList();
    }

    // ---- university ----

    @Transactional
    public void accept(String caseId, ProjectRequests.Accept request, User user) {
        Problem problem = mineIn(caseId, user, ProblemStatus.ASSIGNED);
        problem.getAssignment().accept(request.department().strip(), request.facultyLead().strip(),
                request.studentsCount());
        problem.moveTo(ProblemStatus.IN_PROGRESS, actor(user), "Assignment accepted. Work has started.", Instant.now());
    }

    /** Sends the problem back to the admin, who can choose another university. */
    @Transactional
    public void decline(String caseId, String reason, User user) {
        Problem problem = mineIn(caseId, user, ProblemStatus.ASSIGNED);
        problem.setAssignment(null);
        problem.moveTo(ProblemStatus.VALIDATED, actor(user), "Assignment declined: " + reason.strip(), Instant.now());
    }

    @Transactional
    public void submitProposal(String caseId, ProjectRequests.ProposalForm form, User user) {
        Problem problem = mineIn(caseId, user, ProblemStatus.IN_PROGRESS);
        Assignment a = problem.getAssignment();
        Proposal proposal = a.getProposal();
        if (proposal != null && proposal.getStatus() != ProposalStatus.CHANGES_REQUESTED) {
            throw conflict("This proposal can only be changed when the admin asks for changes.");
        }
        String summary = atLeast50(form.summary(), "Summary");
        String approach = atLeast50(form.approach(), "Approach");
        if (proposal == null) a.setProposal(proposal = new Proposal(a));
        proposal.submit(summary, approach, form.durationWeeks(),
                form.items().stream().map(i -> new BudgetItem(i.label().strip(), i.amount())).toList(), Instant.now());
        problem.record(actor(user), "Proposal submitted for admin review.", Instant.now());
    }

    @Transactional
    public void completeMilestone(String caseId, long milestoneId, String note, User user) {
        Problem problem = mineIn(caseId, user, ProblemStatus.IN_PROGRESS);
        Milestone next = problem.getAssignment().getMilestones().stream().filter(m -> !m.isDone()).findFirst()
                .orElseThrow(() -> conflict("All milestones are already complete."));
        if (next.getId() != milestoneId) throw conflict("Milestones must be completed in order.");
        Instant now = Instant.now();
        next.complete(note.strip(), now);
        problem.record(actor(user), "Milestone completed: " + next.getTitle() + ". " + note.strip(), now);
    }

    @Transactional
    public void requestCompletion(String caseId, User user) {
        Problem problem = mineIn(caseId, user, ProblemStatus.IN_PROGRESS);
        Assignment a = problem.getAssignment();
        if (a.getMilestones().isEmpty() || !a.getMilestones().stream().allMatch(Milestone::isDone)) {
            throw conflict("Complete every milestone first.");
        }
        if (a.getProposal() == null || a.getProposal().getStatus() != ProposalStatus.APPROVED) {
            throw conflict("An admin must approve the proposal first.");
        }
        if (a.getCompletionRequestedAt() != null) throw conflict("Completion has already been requested.");
        Instant now = Instant.now();
        a.setCompletionRequestedAt(now);
        problem.record(actor(user), "Completion requested. Waiting for admin confirmation.", now);
    }

    // ---- admin ----

    @Transactional
    public void reviewProposal(String caseId, boolean approve, String note, User admin) {
        Problem problem = locked(caseId, ProblemStatus.IN_PROGRESS);
        Proposal proposal = problem.getAssignment().getProposal();
        if (proposal == null || proposal.getStatus() != ProposalStatus.SUBMITTED) {
            throw conflict("There is no proposal waiting for review.");
        }
        proposal.review(approve ? ProposalStatus.APPROVED : ProposalStatus.CHANGES_REQUESTED,
                note == null || note.isBlank() ? null : note.strip());
        problem.record(actor(admin), approve ? "Proposal approved." : "Changes requested to the proposal.", Instant.now());
    }

    @Transactional
    public void confirmCompletion(String caseId, String remarks, User admin) {
        Problem problem = awaitingConfirmation(caseId);
        problem.getPledges().stream().filter(Pledge::isPending)
                .forEach(p -> p.close(PledgeStatus.DECLINED, "The project finished before this offer was answered.", Instant.now()));
        problem.moveTo(ProblemStatus.SOLVED, actor(admin),
                remarks == null || remarks.isBlank() ? "Solution verified on site." : remarks.strip(), Instant.now());
    }

    /** Reopens the last milestone, so the university must show new work before asking again. */
    @Transactional
    public void returnForWork(String caseId, String reason, User admin) {
        Problem problem = awaitingConfirmation(caseId);
        Assignment a = problem.getAssignment();
        a.setCompletionRequestedAt(null);
        a.getMilestones().getLast().reopen();
        problem.record(actor(admin), "Returned for more work: " + reason.strip(), Instant.now());
    }

    // ---- rules ----

    private Problem awaitingConfirmation(String caseId) {
        Problem problem = locked(caseId, ProblemStatus.IN_PROGRESS);
        if (problem.getAssignment().getCompletionRequestedAt() == null) {
            throw conflict("The university has not requested completion.");
        }
        return problem;
    }

    private Problem mineIn(String caseId, User user, ProblemStatus expected) {
        Problem problem = problems.findForUpdate(caseId).orElseThrow(ProjectService::notFound);
        // Ownership first, so other universities learn nothing about the status
        if (problem.getAssignment() == null || !isMine(problem.getAssignment(), user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This problem is not assigned to your university.");
        }
        return require(problem, expected);
    }

    private Problem locked(String caseId, ProblemStatus expected) {
        return require(problems.findForUpdate(caseId).orElseThrow(ProjectService::notFound), expected);
    }

    private static Problem require(Problem problem, ProblemStatus expected) {
        if (problem.getStatus() != expected) throw conflict("This problem's status has changed. Please reload the page.");
        return problem;
    }

    private static boolean isMine(Assignment a, User user) {
        return user.getRole() == Role.UNIVERSITY && user.getOrganization() != null
                && a.getOrganization().getId().equals(user.getOrganization().getId());
    }

    private static String actor(User user) {
        return user.getRole() == Role.ADMIN ? user.getName() + " (Govt. Admin)"
                : user.getOrganization().getName();
    }

    private static String atLeast50(String value, String field) {
        String clean = value.strip();
        if (clean.length() < 50) throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be at least 50 characters.");
        return clean;
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "This problem does not exist.");
    }
}
