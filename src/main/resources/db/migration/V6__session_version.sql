-- Signing out raises this number, which makes every older session token invalid
ALTER TABLE users ADD COLUMN session_version INT DEFAULT 0 NOT NULL;
