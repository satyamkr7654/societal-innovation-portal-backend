package com.sip.portal.problem;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "feedback")
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    private Problem problem;
    private int rating;
    private String remarks;
    private Boolean verifiedOnGround;
    private Instant submittedAt;

    protected Feedback() {}

    public Feedback(Problem problem, int rating, String remarks, Boolean verifiedOnGround, Instant submittedAt) {
        this.problem = problem;
        this.rating = rating;
        this.remarks = remarks;
        this.verifiedOnGround = verifiedOnGround;
        this.submittedAt = submittedAt;
    }

    public int getRating() { return rating; }
    public String getRemarks() { return remarks; }
    public Boolean getVerifiedOnGround() { return verifiedOnGround; }
    public Instant getSubmittedAt() { return submittedAt; }
}
