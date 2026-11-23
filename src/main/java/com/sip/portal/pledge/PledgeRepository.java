package com.sip.portal.pledge;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PledgeRepository extends JpaRepository<Pledge, Long> {
    @EntityGraph(attributePaths = {"organization", "problem"})
    List<Pledge> findByOrganizationIdOrderByIdDesc(Long organizationId);
}
