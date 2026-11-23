package com.sip.portal.problem;

import com.sip.portal.pledge.Pledge;
import com.sip.portal.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "problems")
public class Problem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String caseId;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private Category category;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProblemStatus status;
    private String rejectionReason;
    private String district;
    private String address;
    private double latitude;
    private double longitude;
    private String photoUrl;
    @ManyToOne(fetch = FetchType.LAZY)
    private User citizen;
    private Instant submittedAt;
    @Version
    private long version;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL)
    @OrderBy("changedAt, id")
    private Set<StatusHistory> history = new LinkedHashSet<>();
    @OneToOne(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private Assignment assignment;
    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL)
    @OrderBy("id")
    private Set<Pledge> pledges = new LinkedHashSet<>();
    @OneToOne(mappedBy = "problem", cascade = CascadeType.ALL)
    private Feedback feedback;

    protected Problem() {}

    public Problem(User citizen, String title, String description, Category category, String district,
            String address, double latitude, double longitude, String photoUrl, Instant submittedAt) {
        this.citizen = citizen;
        this.title = title;
        this.description = description;
        this.category = category;
        this.district = district;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.photoUrl = photoUrl;
        this.submittedAt = submittedAt;
        this.status = ProblemStatus.SUBMITTED;
    }

    /** Needs the generated id, so it runs after the first save. */
    public void assignCaseId(int year) {
        caseId = "JH-%d-%s-%04d".formatted(year, category.code(), 100 + id);
    }

    /** The only way to change status, so every change is recorded. */
    public void moveTo(ProblemStatus next, String changedBy, String remarks, Instant when) {
        status = next;
        history.add(new StatusHistory(this, next, changedBy, remarks, when));
    }

    /** Adds a history entry without changing status. */
    public void record(String changedBy, String remarks, Instant when) {
        history.add(new StatusHistory(this, status, changedBy, remarks, when));
    }

    public void setRejectionReason(String reason) { rejectionReason = reason; }
    public void setAssignment(Assignment value) { assignment = value; }
    public void setFeedback(Feedback value) { feedback = value; }

    public String getCaseId() { return caseId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public ProblemStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public String getDistrict() { return district; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPhotoUrl() { return photoUrl; }
    public User getCitizen() { return citizen; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Set<StatusHistory> getHistory() { return history; }
    public Assignment getAssignment() { return assignment; }
    public Set<Pledge> getPledges() { return pledges; }
    public Feedback getFeedback() { return feedback; }
}
