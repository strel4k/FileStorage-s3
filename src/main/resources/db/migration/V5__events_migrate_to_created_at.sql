ALTER TABLE events
    ADD COLUMN created_at DATETIME NULL,
    ADD COLUMN message VARCHAR(255) NULL;

UPDATE events SET created_at = timestamp;

ALTER TABLE events
    MODIFY created_at DATETIME NOT NULL;

DROP INDEX idx_events_ts ON events;
CREATE INDEX idx_events_created_at ON events(created_at);