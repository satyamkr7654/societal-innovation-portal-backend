package com.sip.portal.notification;

import com.sip.portal.problem.Problem;
import com.sip.portal.user.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** Saves notifications in the caller's transaction: if the change is rolled back, so are they. */
@Component
public class Notifier {
    private final NotificationRepository notifications;
    private final UserRepository users;

    Notifier(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    public void citizen(Problem problem, String message) {
        send(List.of(problem.getCitizen()), problem, message);
    }

    public void admins(Problem problem, String message) {
        send(users.findActiveByRole(Role.ADMIN), problem, message);
    }

    public void organization(Organization organization, Problem problem, String message) {
        send(users.findActiveByOrganization(organization.getId()), problem, message);
    }

    private void send(Collection<User> recipients, Problem problem, String message) {
        String text = message.length() <= 300 ? message : message.substring(0, 297) + "...";
        Instant now = Instant.now();
        notifications.saveAll(recipients.stream().map(u -> new Notification(u, problem, text, now)).toList());
    }
}
