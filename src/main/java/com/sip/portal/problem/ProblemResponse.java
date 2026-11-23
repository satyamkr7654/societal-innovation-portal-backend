package com.sip.portal.problem;

import java.time.Instant;
import java.util.List;

/** What any signed-in user may see. Deliberately has no phone or email. */
public record ProblemResponse(String id, String title, String description, String category, String status,
        String rejectionReason, String district, Location location, Citizen citizen, University assignedUniversity,
        Support industrySupport, String photoUrl, Instant submittedAt, List<History> statusHistory, Review feedback) {

    public record Location(String address, double lat, double lng) {}
    public record Citizen(String id, String name) {}
    public record University(Long id, String name, String department, String facultyLead, Integer studentsCount) {}
    public record Support(String sponsor, String type, long amount) {}
    public record History(String stage, Instant changedAt, String changedBy, String remarks) {}
    public record Review(int rating, String remarks, Boolean verifiedOnGround, Instant submittedAt) {}

    public static ProblemResponse of(Problem p) {
        Assignment a = p.getAssignment();
        IndustrySupport s = p.getSupport();
        Feedback f = p.getFeedback();
        return new ProblemResponse(p.getCaseId(), p.getTitle(), p.getDescription(), p.getCategory().label(),
                p.getStatus().name(), p.getRejectionReason(), p.getDistrict(),
                new Location(p.getAddress(), p.getLatitude(), p.getLongitude()),
                new Citizen(p.getCitizen().getId(), p.getCitizen().getName()),
                a == null ? null : new University(a.getOrganization().getId(), a.getOrganization().getName(),
                        a.getDepartment(), a.getFacultyLead(), a.getStudentsCount()),
                s == null ? null : new Support(s.getOrganization().getName(), s.getSupportType(), s.getAmountInr()),
                p.getPhotoUrl(), p.getSubmittedAt(),
                p.getHistory().stream().map(h -> new History(h.getStage().name(), h.getChangedAt(), h.getChangedBy(),
                        h.getRemarks())).toList(),
                f == null ? null : new Review(f.getRating(), f.getRemarks(), f.getVerifiedOnGround(),
                        f.getSubmittedAt()));
    }
}
