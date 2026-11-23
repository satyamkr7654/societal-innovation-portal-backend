CREATE TABLE organizations (
  id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  type VARCHAR(20)  NOT NULL,
  CONSTRAINT uq_organization UNIQUE (type, name)
);

CREATE TABLE users (
  id              VARCHAR(36)  PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,
  email           VARCHAR(190) NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  role            VARCHAR(20)  NOT NULL,
  phone           VARCHAR(15),
  organization_id BIGINT,
  created_at      DATETIME(6)  NOT NULL,
  CONSTRAINT uq_user_email UNIQUE (email),
  CONSTRAINT fk_user_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE problems (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id          VARCHAR(30),
  title            VARCHAR(120)  NOT NULL,
  description      VARCHAR(2000) NOT NULL,
  category         VARCHAR(20)   NOT NULL,
  status           VARCHAR(20)   NOT NULL,
  rejection_reason VARCHAR(500),
  district         VARCHAR(40)   NOT NULL,
  address          VARCHAR(200)  NOT NULL,
  latitude         DOUBLE        NOT NULL,
  longitude        DOUBLE        NOT NULL,
  photo_url        VARCHAR(255),
  citizen_id       VARCHAR(36)   NOT NULL,
  submitted_at     DATETIME(6)   NOT NULL,
  version          BIGINT        NOT NULL,
  CONSTRAINT uq_problem_case UNIQUE (case_id),
  CONSTRAINT fk_problem_citizen FOREIGN KEY (citizen_id) REFERENCES users (id)
);
CREATE INDEX ix_problem_status ON problems (status);
CREATE INDEX ix_problem_district ON problems (district);

CREATE TABLE status_history (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id BIGINT       NOT NULL,
  stage      VARCHAR(20)  NOT NULL,
  changed_by VARCHAR(150) NOT NULL,
  remarks    VARCHAR(500) NOT NULL,
  changed_at DATETIME(6)  NOT NULL,
  CONSTRAINT fk_history_problem FOREIGN KEY (problem_id) REFERENCES problems (id)
);

CREATE TABLE assignments (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id      BIGINT NOT NULL,
  organization_id BIGINT NOT NULL,
  department      VARCHAR(150),
  faculty_lead    VARCHAR(100),
  students_count  INT,
  CONSTRAINT uq_assignment_problem UNIQUE (problem_id),
  CONSTRAINT fk_assignment_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
  CONSTRAINT fk_assignment_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE industry_support (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id      BIGINT       NOT NULL,
  organization_id BIGINT       NOT NULL,
  support_type    VARCHAR(150) NOT NULL,
  amount_inr      BIGINT       NOT NULL,
  CONSTRAINT uq_support_problem UNIQUE (problem_id),
  CONSTRAINT fk_support_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
  CONSTRAINT fk_support_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE feedback (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id         BIGINT      NOT NULL,
  rating             INT         NOT NULL,
  remarks            VARCHAR(1000),
  verified_on_ground BOOLEAN,
  submitted_at       DATETIME(6) NOT NULL,
  CONSTRAINT uq_feedback_problem UNIQUE (problem_id),
  CONSTRAINT fk_feedback_problem FOREIGN KEY (problem_id) REFERENCES problems (id),
  CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
