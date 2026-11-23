package com.sip.portal.notification;

import com.sip.portal.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Always limited to the signed-in user's own notifications. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    record Item(Long id, String message, String problemId, Instant createdAt, boolean read) {}
    record Unread(long count) {}

    private final NotificationRepository notifications;

    NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Item> latest(@AuthenticationPrincipal User user) {
        return notifications.findTop50ByUserIdOrderByIdDesc(user.getId()).stream().map(n -> new Item(n.getId(),
                n.getMessage(), n.getProblem() == null ? null : n.getProblem().getCaseId(), n.getCreatedAt(),
                n.getReadAt() != null)).toList();
    }

    @GetMapping("/unread-count")
    public Unread unread(@AuthenticationPrincipal User user) {
        return new Unread(notifications.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void read(@PathVariable long id, @AuthenticationPrincipal User user) {
        notifications.markRead(id, user.getId(), Instant.now());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void readAll(@AuthenticationPrincipal User user) {
        notifications.markAllRead(user.getId(), Instant.now());
    }
}
