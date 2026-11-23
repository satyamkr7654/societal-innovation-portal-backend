package com.sip.portal.project;

import com.sip.portal.problem.Assignment;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "proposals")
public class Proposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    private Assignment assignment;
    private String summary;
    private String approach;
    private int durationWeeks;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProposalStatus status;
    private String reviewNote;
    private Instant submittedAt;
    @ElementCollection
    @CollectionTable(name = "proposal_items", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<BudgetItem> items = new ArrayList<>();

    protected Proposal() {}

    public Proposal(Assignment assignment) {
        this.assignment = assignment;
    }

    public void submit(String summary, String approach, int durationWeeks, List<BudgetItem> newItems, Instant when) {
        this.summary = summary;
        this.approach = approach;
        this.durationWeeks = durationWeeks;
        items.clear();
        items.addAll(newItems);
        status = ProposalStatus.SUBMITTED;
        reviewNote = null;
        submittedAt = when;
    }

    public void review(ProposalStatus decision, String note) {
        status = decision;
        reviewNote = note;
    }

    public String getSummary() { return summary; }
    public String getApproach() { return approach; }
    public int getDurationWeeks() { return durationWeeks; }
    public ProposalStatus getStatus() { return status; }
    public String getReviewNote() { return reviewNote; }
    public Instant getSubmittedAt() { return submittedAt; }
    public List<BudgetItem> getItems() { return items; }
}
