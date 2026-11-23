-- Room for a 500-character reason plus a short prefix
ALTER TABLE status_history MODIFY COLUMN remarks VARCHAR(600) NOT NULL;

ALTER TABLE assignments ADD COLUMN completion_requested_at DATETIME(6);

CREATE TABLE milestones (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  assignment_id BIGINT       NOT NULL,
  position      INT          NOT NULL,
  title         VARCHAR(100) NOT NULL,
  note          VARCHAR(500),
  completed_at  DATETIME(6),
  CONSTRAINT uq_milestone UNIQUE (assignment_id, position),
  CONSTRAINT fk_milestone_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
);

CREATE TABLE proposals (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  assignment_id  BIGINT        NOT NULL,
  summary        VARCHAR(2000) NOT NULL,
  approach       VARCHAR(3000) NOT NULL,
  duration_weeks INT           NOT NULL,
  status         VARCHAR(20)   NOT NULL,
  review_note    VARCHAR(500),
  submitted_at   DATETIME(6)   NOT NULL,
  CONSTRAINT uq_proposal_assignment UNIQUE (assignment_id),
  CONSTRAINT fk_proposal_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
);

CREATE TABLE proposal_items (
  proposal_id BIGINT       NOT NULL,
  position    INT          NOT NULL,
  label       VARCHAR(100) NOT NULL,
  amount_inr  BIGINT       NOT NULL,
  PRIMARY KEY (proposal_id, position),
  CONSTRAINT fk_item_proposal FOREIGN KEY (proposal_id) REFERENCES proposals (id),
  CONSTRAINT ck_item_amount CHECK (amount_inr > 0)
);

-- Projects that were already running get the standard milestones
INSERT INTO milestones (assignment_id, position, title)
SELECT a.id, m.position, m.title
FROM assignments a
JOIN problems p ON p.id = a.problem_id AND p.status = 'IN_PROGRESS'
CROSS JOIN (
  SELECT 1 AS position, 'Design' AS title UNION ALL SELECT 2, 'Prototype'
  UNION ALL SELECT 3, 'Field Deployment' UNION ALL SELECT 4, 'Citizen Handover'
) m;
