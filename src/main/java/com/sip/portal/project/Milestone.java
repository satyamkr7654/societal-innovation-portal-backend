package com.sip.portal.project;

import com.sip.portal.problem.Assignment;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "milestones")
public class Milestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Assignment assignment;
    private int position;
    private String title;
    private String note;
    private Instant completedAt;

    protected Milestone() {}

    public Milestone(Assignment assignment, int position, String title) {
        this.assignment = assignment;
        this.position = position;
        this.title = title;
    }

    public void complete(String text, Instant when) {
        note = text;
        completedAt = when;
    }

    public void reopen() {
        note = null;
        completedAt = null;
    }

    public boolean isDone() { return completedAt != null; }
    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public Instant getCompletedAt() { return completedAt; }
}
