ALTER TABLE users ADD COLUMN display_name VARCHAR(150);

UPDATE users SET display_name = username WHERE display_name IS NULL;
