package com.sip.portal.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @EntityGraph(attributePaths = "problem")
    List<Notification> findTop50ByUserIdOrderByIdDesc(String userId);

    long countByUserIdAndReadAtIsNull(String userId);

    // The user id in both updates stops anyone marking another person's notifications
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.user.id = :userId and n.readAt is null")
    int markRead(long id, String userId, Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(String userId, Instant now);
}
