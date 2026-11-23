package com.sip.portal.project;

import com.sip.portal.pledge.*;
import com.sip.portal.problem.*;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/** Work on one assigned problem. `proposal` and `funding` are null, and `pledges` empty, for people who may not see them. */
public record ProjectResponse(boolean canManage, Instant completionRequestedAt, List<Step> milestones, Plan proposal,
        Funding funding, List<PledgeResponse> pledges) {
    public record Step(Long id, String title, boolean done, String note, Instant completedAt) {}
    public record Item(String label, long amount) {}
    public record Plan(String summary, String approach, int durationWeeks, List<Item> items, long total, String status,
            String reviewNote, Instant submittedAt) {}

    static ProjectResponse of(Problem problem, boolean canManage, boolean canSeeProposal,
            Predicate<Pledge> canSeePledge) {
        Assignment a = problem.getAssignment();
        Proposal p = canSeeProposal ? a.getProposal() : null;
        return new ProjectResponse(canManage, a.getCompletionRequestedAt(),
                a.getMilestones().stream().map(m -> new Step(m.getId(), m.getTitle(), m.isDone(), m.getNote(),
                        m.getCompletedAt())).toList(),
                p == null ? null : new Plan(p.getSummary(), p.getApproach(), p.getDurationWeeks(),
                        p.getItems().stream().map(i -> new Item(i.label(), i.amountInr())).toList(),
                        p.getItems().stream().mapToLong(BudgetItem::amountInr).sum(), p.getStatus().name(),
                        p.getReviewNote(), p.getSubmittedAt()),
                p == null ? null : Funding.of(problem),
                problem.getPledges().stream().filter(canSeePledge).map(PledgeResponse::of).toList());
    }
}
