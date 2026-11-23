ALTER TABLE users ADD COLUMN account_status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE users ADD COLUMN reviewed_by VARCHAR(36);
ALTER TABLE users ADD COLUMN reviewed_at DATETIME(6);
ALTER TABLE users ADD CONSTRAINT fk_user_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id);

-- Organisations registered before this rule existed were never checked
UPDATE users SET account_status = 'PENDING' WHERE role IN ('UNIVERSITY', 'INDUSTRY');

CREATE INDEX ix_user_account_status ON users (account_status);
