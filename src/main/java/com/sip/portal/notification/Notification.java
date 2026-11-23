package com.sip.portal.notification;

import com.sip.portal.problem.Problem;
import com.sip.portal.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    private Problem problem;
    private String message;
    private Instant createdAt;
    private Instant readAt;

    protected Notification() {}

    public Notification(User user, Problem problem, String message, Instant createdAt) {
        this.user = user;
        this.problem = problem;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Problem getProblem() { return problem; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
