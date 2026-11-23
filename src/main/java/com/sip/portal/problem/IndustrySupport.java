package com.sip.portal.problem;

import com.sip.portal.user.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "industry_support")
public class IndustrySupport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    private Problem problem;
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
    private String supportType;
    private long amountInr;

    protected IndustrySupport() {}

    public IndustrySupport(Problem problem, Organization organization, String supportType, long amountInr) {
        this.problem = problem;
        this.organization = organization;
        this.supportType = supportType;
        this.amountInr = amountInr;
    }

    public Organization getOrganization() { return organization; }
    public String getSupportType() { return supportType; }
    public long getAmountInr() { return amountInr; }
}
