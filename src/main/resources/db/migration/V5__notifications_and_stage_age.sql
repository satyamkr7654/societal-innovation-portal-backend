CREATE TABLE notifications (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  problem_id BIGINT,
  message    VARCHAR(300) NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  read_at    DATETIME(6),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_notification_problem FOREIGN KEY (problem_id) REFERENCES problems (id)
);
CREATE INDEX ix_notification_user ON notifications (user_id, read_at);

-- When the current status began: the first entry after the last one of any other status
ALTER TABLE problems ADD COLUMN status_since DATETIME(6);
UPDATE problems SET status_since = COALESCE((
  SELECT MIN(h.changed_at) FROM status_history h
  WHERE h.problem_id = problems.id AND h.stage = problems.status AND h.changed_at >= COALESCE((
    SELECT MAX(o.changed_at) FROM status_history o
    WHERE o.problem_id = problems.id AND o.stage <> problems.status), problems.submitted_at)
), problems.submitted_at);
ALTER TABLE problems MODIFY COLUMN status_since DATETIME(6) NOT NULL;

CREATE INDEX ix_problem_location ON problems (category, latitude, longitude);
