package com.sip.portal.pledge;

import com.sip.portal.problem.Problem;
import com.sip.portal.project.BudgetItem;
import com.sip.portal.project.Proposal;

/** Budget against accepted funding. Pending offers do not count. */
public record Funding(long needed, long accepted, long remaining) {
    public static Funding of(Problem problem) {
        Proposal proposal = problem.getAssignment().getProposal();
        long needed = proposal.getItems().stream().mapToLong(BudgetItem::amountInr).sum();
        long accepted = problem.getPledges().stream()
                .filter(p -> p.isAccepted() && p.getType() == PledgeType.FUNDING).mapToLong(Pledge::getAmountInr).sum();
        return new Funding(needed, accepted, Math.max(0, needed - accepted));
    }
}
