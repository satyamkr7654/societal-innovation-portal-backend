package com.sip.portal.problem;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "status_history")
public class StatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Problem problem;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProblemStatus stage;
    private String changedBy;
    private String remarks;
    private Instant changedAt;

    protected StatusHistory() {}

    StatusHistory(Problem problem, ProblemStatus stage, String changedBy, String remarks, Instant changedAt) {
        this.problem = problem;
        this.stage = stage;
        this.changedBy = changedBy;
        this.remarks = remarks;
        this.changedAt = changedAt;
    }

    public ProblemStatus getStage() { return stage; }
    public String getChangedBy() { return changedBy; }
    public String getRemarks() { return remarks; }
    public Instant getChangedAt() { return changedAt; }
}
