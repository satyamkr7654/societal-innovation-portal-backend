package com.sip.portal.pledge;

import com.sip.portal.problem.Problem;
import com.sip.portal.user.Organization;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An industry offer. Only FUNDING has an amount. */
@Entity
@Table(name = "pledges")
public class Pledge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Problem problem;
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private PledgeType type;
    private Long amountInr;
    private String description;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private PledgeStatus status;
    private String responseNote;
    private Instant createdAt;
    private Instant respondedAt;

    protected Pledge() {}

    public Pledge(Problem problem, Organization organization, PledgeType type, Long amountInr, String description,
            Instant createdAt) {
        this.problem = problem;
        this.organization = organization;
        this.type = type;
        this.amountInr = amountInr;
        this.description = description;
        this.status = PledgeStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void close(PledgeStatus outcome, String note, Instant when) {
        status = outcome;
        responseNote = note;
        respondedAt = when;
    }

    public boolean isPending() { return status == PledgeStatus.PENDING; }
    public boolean isAccepted() { return status == PledgeStatus.ACCEPTED; }
    public boolean isFrom(Organization other) {
        return other != null && organization.getId().equals(other.getId());
    }

    public Long getId() { return id; }
    public Problem getProblem() { return problem; }
    public Organization getOrganization() { return organization; }
    public PledgeType getType() { return type; }
    public Long getAmountInr() { return amountInr; }
    public String getDescription() { return description; }
    public PledgeStatus getStatus() { return status; }
    public String getResponseNote() { return responseNote; }
    public Instant getCreatedAt() { return createdAt; }
}
