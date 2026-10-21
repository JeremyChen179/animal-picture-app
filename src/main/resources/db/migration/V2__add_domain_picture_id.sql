-- V2: the domain identity of a picture (PictureId, a UUID) becomes a column of its own.
--  * picture_id: assigned by the application when a picture is fetched; this is the id used everywhere.
--  * id: the database's internal row id. Kept only as a storage detail (insertion order); it never
--    leaves the persistence adapter.
-- SQLite can't add a NOT NULL UNIQUE column in place, so the table is rebuilt. Existing rows get a
-- random version-4 UUID.
CREATE TABLE picture_v2 (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    picture_id     TEXT    NOT NULL UNIQUE,
    animal_type    TEXT    NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
    image          BLOB    NOT NULL,
    content_type   TEXT    NOT NULL,
    source_website TEXT    NOT NULL,
    source_url     TEXT    NOT NULL,
    created_at     TEXT    NOT NULL
);

INSERT INTO picture_v2 (id, picture_id, animal_type, image, content_type, source_website, source_url, created_at)
SELECT id,
       lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-'
             || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
       animal_type, image, content_type, source_website, source_url, created_at
FROM picture;

DROP TABLE picture;
ALTER TABLE picture_v2 RENAME TO picture;

CREATE INDEX idx_picture_created_at ON picture (created_at);
CREATE INDEX idx_picture_animal_created_at ON picture (animal_type, created_at);
