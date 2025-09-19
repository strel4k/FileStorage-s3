ALTER TABLE files
    ADD COLUMN owner_id INT NULL AFTER status,
    ADD CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users(id)