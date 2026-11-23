package com.sip.portal.problem;

import com.sip.portal.project.Milestone;
import com.sip.portal.project.Proposal;
import com.sip.portal.user.Organization;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
public class Assignment {
    private static final List<String> STAGES = List.of("Design", "Prototype", "Field Deployment", "Citizen Handover");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    private Problem problem;
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
    private String department;
    private String facultyLead;
    private Integer studentsCount;
    private Instant completionRequestedAt;
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Milestone> milestones = new ArrayList<>();
    @OneToOne(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Proposal proposal;

    protected Assignment() {}

    public Assignment(Problem problem, Organization organization, String department, String facultyLead,
            Integer studentsCount) {
        this.problem = problem;
        this.organization = organization;
        this.department = department;
        this.facultyLead = facultyLead;
        this.studentsCount = studentsCount;
    }

    /** Records the team and creates the standard milestones. */
    public void accept(String department, String facultyLead, int studentsCount) {
        this.department = department;
        this.facultyLead = facultyLead;
        this.studentsCount = studentsCount;
        addMilestones();
    }

    public void addMilestones() {
        for (String title : STAGES) milestones.add(new Milestone(this, milestones.size() + 1, title));
    }

    public void setCompletionRequestedAt(Instant when) { completionRequestedAt = when; }
    public void setProposal(Proposal value) { proposal = value; }
    public Instant getCompletionRequestedAt() { return completionRequestedAt; }
    public List<Milestone> getMilestones() { return milestones; }
    public Proposal getProposal() { return proposal; }
    public Organization getOrganization() { return organization; }
    public String getDepartment() { return department; }
    public String getFacultyLead() { return facultyLead; }
    public Integer getStudentsCount() { return studentsCount; }
}
