ALTER TABLE users
  ADD COLUMN password_hash VARCHAR(100) NOT NULL AFTER username;

ALTER TABLE users
  ADD CONSTRAINT uk_users_username UNIQUE (username);