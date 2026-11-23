package com.sip.portal.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, String> {
    @EntityGraph(attributePaths = "organization")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(String id);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByAccountStatus(AccountStatus status);

    @EntityGraph(attributePaths = "organization")
    List<User> findByAccountStatusOrderByCreatedAt(AccountStatus status);

    @Modifying
    @Query("""
            update User u set u.accountStatus = :next, u.reviewedBy = :adminId, u.reviewedAt = :now
            where u.id = :id and u.accountStatus = com.sip.portal.user.AccountStatus.PENDING""")
    int decidePending(String id, AccountStatus next, String adminId, Instant now);
}
