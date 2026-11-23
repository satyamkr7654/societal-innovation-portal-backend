package com.sip.portal.problem;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "assignment.proposal", "feedback"})
    List<Problem> findAllByOrderBySubmittedAtDesc();

    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "assignment.proposal", "feedback"})
    Optional<Problem> findByCaseId(String caseId);

    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "assignment.proposal", "feedback"})
    @Query("select p from Problem p where p.assignment.organization.id = :organizationId order by p.submittedAt desc")
    List<Problem> findByAssignedOrganization(long organizationId);

    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "assignment.proposal", "feedback"})
    @Query("""
            select p from Problem p where p.status = com.sip.portal.problem.ProblemStatus.IN_PROGRESS
            and p.assignment.completionRequestedAt is null
            and p.assignment.proposal.status = com.sip.portal.project.ProposalStatus.APPROVED
            order by p.submittedAt""")
    List<Problem> findOpenForSupport();

    @Query("""
            select count(p) from Problem p join p.assignment a join a.proposal r
            where p.status = com.sip.portal.problem.ProblemStatus.IN_PROGRESS
            and r.status = com.sip.portal.project.ProposalStatus.SUBMITTED""")
    long countProposalsToReview();

    @Query("""
            select count(p) from Problem p join p.assignment a
            where p.status = com.sip.portal.problem.ProblemStatus.IN_PROGRESS and a.completionRequestedAt is not null""")
    long countCompletionsToConfirm();

    /** Use before changing a problem: simultaneous changes then wait in turn instead of deadlocking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.caseId = :caseId")
    Optional<Problem> findForUpdate(String caseId);

    interface SolvedTime {
        Instant getSubmittedAt();
        Instant getSolvedAt();
    }

    @Query("select p.status, count(p) from Problem p group by p.status")
    List<Object[]> countByStatus();

    @Query("select p.district, count(p) from Problem p group by p.district order by count(p) desc, p.district")
    List<Object[]> countByDistrict();

    @Query("select p.category, count(p) from Problem p group by p.category order by count(p) desc, p.category")
    List<Object[]> countByCategory();

    @Query("""
            select count(distinct p.problem) from Pledge p where p.type = com.sip.portal.pledge.PledgeType.FUNDING
            and p.status = com.sip.portal.pledge.PledgeStatus.ACCEPTED""")
    long countFunded();

    @Query("""
            select coalesce(sum(p.amountInr), 0) from Pledge p where p.type = com.sip.portal.pledge.PledgeType.FUNDING
            and p.status = com.sip.portal.pledge.PledgeStatus.ACCEPTED""")
    long totalFundingInr();

    @Query("select avg(f.rating) from Feedback f")
    Double averageRating();

    @Query("""
            select p.submittedAt as submittedAt, max(h.changedAt) as solvedAt from Problem p join p.history h
            where p.status = com.sip.portal.problem.ProblemStatus.SOLVED
            and h.stage = com.sip.portal.problem.ProblemStatus.SOLVED group by p.id, p.submittedAt""")
    List<SolvedTime> findSolvedTimes();
}
