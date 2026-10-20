package com.example.animalpictures.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plain-JDBC access to the {@code picture} table defined in {@code schema.sql}. */
@Repository
public class PictureRepository {

    /**
     * Fixed-width ISO-8601 UTC (always 9 fraction digits). {@link Instant#toString()} drops trailing
     * zeros, which would break chronological ordering of the text column.
     */
    static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private static final String SELECT_COLUMNS =
            "SELECT id, animal_type, image, content_type, source_website, source_url, created_at FROM picture ";

    private static final RowMapper<Picture> ROW_MAPPER = PictureRepository::mapRow;

    private final JdbcClient jdbc;

    public PictureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Picture save(Picture picture) {
        long id = jdbc.sql("""
                        INSERT INTO picture (animal_type, image, content_type, source_website, source_url, created_at)
                        VALUES (:animalType, :image, :contentType, :sourceWebsite, :sourceUrl, :createdAt)
                        RETURNING id""")
                .param("animalType", picture.animalType().name())
                .param("image", picture.image())
                .param("contentType", picture.contentType())
                .param("sourceWebsite", picture.sourceWebsite())
                .param("sourceUrl", picture.sourceUrl())
                .param("createdAt", TIMESTAMP_FORMAT.format(picture.createdAt()))
                .query(Long.class)
                .single();
        return picture.withId(id);
    }

    /** Saves all pictures in one short transaction: either all are stored or none. */
    @Transactional
    public List<Picture> saveAll(List<Picture> pictures) {
        return pictures.stream().map(this::save).toList();
    }

    /** Latest picture of any animal. The id breaks ties between pictures with the same timestamp. */
    public Optional<Picture> findLatest() {
        return jdbc.sql(SELECT_COLUMNS + "ORDER BY created_at DESC, id DESC LIMIT 1")
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<Picture> findLatestByAnimalType(AnimalType animalType) {
        return jdbc.sql(SELECT_COLUMNS + "WHERE animal_type = :animalType ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("animalType", animalType.name())
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<Picture> findById(long id) {
        return jdbc.sql(SELECT_COLUMNS + "WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    /**
     * Most recent pictures first, optionally for one animal. Reads {@code length(image)} instead of the
     * image itself, so listing doesn't load every picture's bytes.
     */
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        String where = animalType != null ? "WHERE animal_type = :animalType " : "";
        JdbcClient.StatementSpec statement = jdbc.sql(
                        "SELECT id, animal_type, content_type, length(image) AS size_bytes, source_website, source_url, created_at "
                                + "FROM picture " + where + "ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("limit", limit);
        if (animalType != null) {
            statement = statement.param("animalType", animalType.name());
        }
        return statement.query((rs, rowNum) -> new PictureSummary(
                        rs.getLong("id"),
                        AnimalType.valueOf(rs.getString("animal_type")),
                        rs.getString("content_type"),
                        rs.getInt("size_bytes"),
                        rs.getString("source_website"),
                        rs.getString("source_url"),
                        Instant.from(TIMESTAMP_FORMAT.parse(rs.getString("created_at")))))
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM picture").query(Long.class).single();
    }

    public void deleteAll() {
        jdbc.sql("DELETE FROM picture").update();
    }

    private static Picture mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Picture(
                rs.getLong("id"),
                AnimalType.valueOf(rs.getString("animal_type")),
                rs.getBytes("image"),
                rs.getString("content_type"),
                rs.getString("source_website"),
                rs.getString("source_url"),
                Instant.from(TIMESTAMP_FORMAT.parse(rs.getString("created_at"))));
    }
}
