-- Schema for the picture store. Runs at every startup (spring.sql.init.mode=always) and is idempotent.
-- SQLite has no native timestamp type: created_at is stored as fixed-width ISO-8601 UTC text
-- (e.g. 2026-10-19T06:10:20.705582087Z), which sorts chronologically as a plain string.
CREATE TABLE IF NOT EXISTS picture (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    animal_type    TEXT    NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
    image          BLOB    NOT NULL,
    content_type   TEXT    NOT NULL,
    source_website TEXT    NOT NULL,
    source_url     TEXT    NOT NULL,
    created_at     TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_picture_created_at ON picture (created_at);
CREATE INDEX IF NOT EXISTS idx_picture_animal_created_at ON picture (animal_type, created_at);
