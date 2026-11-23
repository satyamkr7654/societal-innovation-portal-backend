package com.sip.portal.problem;

import com.sip.portal.user.Organization;
import jakarta.persistence.*;

@Entity
@Table(name = "assignments")
public class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    private Problem problem;
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
    private String department;
    private String facultyLead;
    private Integer studentsCount;

    protected Assignment() {}

    public Assignment(Problem problem, Organization organization, String department, String facultyLead,
            Integer studentsCount) {
        this.problem = problem;
        this.organization = organization;
        this.department = department;
        this.facultyLead = facultyLead;
        this.studentsCount = studentsCount;
    }

    public Organization getOrganization() { return organization; }
    public String getDepartment() { return department; }
    public String getFacultyLead() { return facultyLead; }
    public Integer getStudentsCount() { return studentsCount; }
}
