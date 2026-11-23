CREATE TABLE pledges (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id      BIGINT       NOT NULL,
  organization_id BIGINT       NOT NULL,
  type            VARCHAR(20)  NOT NULL,
  amount_inr      BIGINT,
  description     VARCHAR(500) NOT NULL,
  status          VARCHAR(20)  NOT NULL,
  response_note   VARCHAR(500),
  created_at      DATETIME(6)  NOT NULL,
  responded_at    DATETIME(6),
  CONSTRAINT fk_pledge_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
  CONSTRAINT fk_pledge_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
  CONSTRAINT ck_pledge_amount CHECK (amount_inr > 0)
);
CREATE INDEX ix_pledge_status ON pledges (status);

-- Support recorded before pledges existed was already agreed
INSERT INTO pledges (problem_id, organization_id, type, amount_inr, description, status, created_at, responded_at)
SELECT s.problem_id, s.organization_id, 'FUNDING', s.amount_inr, s.support_type, 'ACCEPTED', p.submitted_at, p.submitted_at
FROM industry_support s JOIN problems p ON p.id = s.problem_id;

DROP TABLE industry_support;
