package com.sip.portal.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByTypeAndName(Role type, String name);

    /** Universities with an approved account, so somebody can act on the assignment. */
    @Query("""
            select o from Organization o where o.type = com.sip.portal.user.Role.UNIVERSITY and exists (
              select 1 from User u where u.organization = o and u.role = com.sip.portal.user.Role.UNIVERSITY
              and u.accountStatus = com.sip.portal.user.AccountStatus.ACTIVE) order by o.name""")
    List<Organization> findAssignableUniversities();

    default Optional<Organization> findAssignableUniversity(long id) {
        return findAssignableUniversities().stream().filter(o -> o.getId() == id).findFirst();
    }
}
