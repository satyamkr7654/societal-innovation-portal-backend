package com.sip.portal.problem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "support.organization", "feedback"})
    List<Problem> findAllByOrderBySubmittedAtDesc();

    @EntityGraph(attributePaths = {"citizen", "assignment.organization", "support.organization", "feedback"})
    Optional<Problem> findByCaseId(String caseId);
}
