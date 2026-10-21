package com.example.animalpictures.adapter.out.persistence;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Image;
import com.example.animalpictures.domain.ImageOrigin;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureId;
import com.example.animalpictures.domain.PictureRepository;
import com.example.animalpictures.domain.PictureSummary;
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

/**
 * SQLite implementation of the domain's {@link PictureRepository} (plain JDBC; schema in
 * {@code db/migration}).
 *
 * <p>Pictures are identified by their domain {@link PictureId} ({@code picture_id} column). The
 * table's integer {@code id} is the database's own row id: it's only used inside this class, as an
 * insertion-order tie-breaker when sorting, and never leaves it.
 */
@Repository
public class SqlitePictureRepository implements PictureRepository {

    /**
     * Fixed-width ISO-8601 UTC (always 9 fraction digits). {@link Instant#toString()} drops trailing
     * zeros, which would break chronological ordering of the text column.
     */
    static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private static final String SELECT_PICTURE =
            "SELECT picture_id, animal_type, image, content_type, source_website, source_url, created_at FROM picture ";
    /** Newest first; the internal row id breaks ties between pictures fetched at the same instant. */
    private static final String NEWEST_FIRST = "ORDER BY created_at DESC, id DESC ";

    private static final RowMapper<Picture> PICTURE_MAPPER = SqlitePictureRepository::mapPicture;

    private final JdbcClient jdbc;

    public SqlitePictureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores all pictures in one short transaction: either all are stored or none. */
    @Override
    @Transactional
    public void saveAll(List<Picture> pictures) {
        pictures.forEach(this::insert);
    }

    private void insert(Picture picture) {
        jdbc.sql("""
                        INSERT INTO picture (picture_id, animal_type, image, content_type, source_website, source_url, created_at)
                        VALUES (:pictureId, :animalType, :image, :contentType, :sourceWebsite, :sourceUrl, :createdAt)""")
                .param("pictureId", picture.id().toString())
                .param("animalType", picture.animalType().name())
                .param("image", picture.image().bytes())
                .param("contentType", picture.image().contentType())
                .param("sourceWebsite", picture.origin().website())
                .param("sourceUrl", picture.origin().url())
                .param("createdAt", TIMESTAMP_FORMAT.format(picture.fetchedAt()))
                .update();
    }

    @Override
    public Optional<Picture> findLatest() {
        return jdbc.sql(SELECT_PICTURE + NEWEST_FIRST + "LIMIT 1").query(PICTURE_MAPPER).optional();
    }

    @Override
    public Optional<Picture> findLatestByAnimalType(AnimalType animalType) {
        return jdbc.sql(SELECT_PICTURE + "WHERE animal_type = :animalType " + NEWEST_FIRST + "LIMIT 1")
                .param("animalType", animalType.name())
                .query(PICTURE_MAPPER)
                .optional();
    }

    @Override
    public Optional<Picture> findById(PictureId id) {
        return jdbc.sql(SELECT_PICTURE + "WHERE picture_id = :pictureId")
                .param("pictureId", id.toString())
                .query(PICTURE_MAPPER)
                .optional();
    }

    /** Reads {@code length(image)} instead of the image itself, so listing doesn't load every picture's bytes. */
    @Override
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        String where = animalType != null ? "WHERE animal_type = :animalType " : "";
        JdbcClient.StatementSpec statement = jdbc.sql(
                        "SELECT picture_id, animal_type, content_type, length(image) AS size_bytes, source_website, source_url, created_at "
                                + "FROM picture " + where + NEWEST_FIRST + "LIMIT :limit")
                .param("limit", limit);
        if (animalType != null) {
            statement = statement.param("animalType", animalType.name());
        }
        return statement.query((rs, rowNum) -> new PictureSummary(
                        PictureId.of(rs.getString("picture_id")),
                        AnimalType.valueOf(rs.getString("animal_type")),
                        rs.getString("content_type"),
                        rs.getInt("size_bytes"),
                        new ImageOrigin(rs.getString("source_website"), rs.getString("source_url")),
                        parseTimestamp(rs.getString("created_at"))))
                .list();
    }

    /** Only for tests and maintenance; not part of the domain repository. */
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM picture").query(Long.class).single();
    }

    /** Only for tests and maintenance; not part of the domain repository. */
    public void deleteAll() {
        jdbc.sql("DELETE FROM picture").update();
    }

    private static Picture mapPicture(ResultSet rs, int rowNum) throws SQLException {
        return Picture.restore(
                PictureId.of(rs.getString("picture_id")),
                AnimalType.valueOf(rs.getString("animal_type")),
                new Image(rs.getBytes("image"), rs.getString("content_type")),
                new ImageOrigin(rs.getString("source_website"), rs.getString("source_url")),
                parseTimestamp(rs.getString("created_at")));
    }

    private static Instant parseTimestamp(String value) {
        return Instant.from(TIMESTAMP_FORMAT.parse(value));
    }
}
