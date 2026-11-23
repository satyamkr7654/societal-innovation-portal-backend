package com.sip.portal.problem;

import com.sip.portal.pledge.Pledge;
import com.sip.portal.project.ProposalStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** What any signed-in user may see. Deliberately has no phone or email. */
public record ProblemResponse(String id, String title, String description, String category, String status,
        String rejectionReason, String district, Location location, Citizen citizen, University assignedUniversity,
        List<Support> supporters, String photoUrl, Instant submittedAt, List<History> statusHistory, Review feedback,
        String pendingDecision, long daysInStage, boolean overdue) {

    public record Location(String address, double lat, double lng) {}
    public record Citizen(String id, String name) {}
    public record University(Long id, String name, String department, String facultyLead, Integer studentsCount) {}
    public record Support(String sponsor, String type, Long amount, String description) {}
    public record History(String stage, Instant changedAt, String changedBy, String remarks) {}
    public record Review(int rating, String remarks, Boolean verifiedOnGround, Instant submittedAt) {}

    public static ProblemResponse of(Problem p) {
        Assignment a = p.getAssignment();
        Feedback f = p.getFeedback();
        return new ProblemResponse(p.getCaseId(), p.getTitle(), p.getDescription(), p.getCategory().label(),
                p.getStatus().name(), p.getRejectionReason(), p.getDistrict(),
                new Location(p.getAddress(), p.getLatitude(), p.getLongitude()),
                new Citizen(p.getCitizen().getId(), p.getCitizen().getName()),
                a == null ? null : new University(a.getOrganization().getId(), a.getOrganization().getName(),
                        a.getDepartment(), a.getFacultyLead(), a.getStudentsCount()),
                p.getPledges().stream().filter(Pledge::isAccepted).map(s -> new Support(s.getOrganization().getName(),
                        s.getType().name(), s.getAmountInr(), s.getDescription())).toList(),
                p.getPhotoUrl(), p.getSubmittedAt(),
                p.getHistory().stream().map(h -> new History(h.getStage().name(), h.getChangedAt(), h.getChangedBy(),
                        h.getRemarks())).toList(),
                f == null ? null : new Review(f.getRating(), f.getRemarks(), f.getVerifiedOnGround(),
                        f.getSubmittedAt()), pendingDecision(p),
                Duration.between(p.getStatusSince(), Instant.now()).toDays(), StageLimits.isOverdue(p, Instant.now()));
    }

    /** What an admin still has to decide about a running project, if anything. */
    private static String pendingDecision(Problem p) {
        Assignment a = p.getAssignment();
        if (a == null || p.getStatus() != ProblemStatus.IN_PROGRESS) return null;
        if (a.getCompletionRequestedAt() != null) return "COMPLETION";
        boolean waiting = a.getProposal() != null && a.getProposal().getStatus() == ProposalStatus.SUBMITTED;
        return waiting ? "PROPOSAL" : null;
    }
}
