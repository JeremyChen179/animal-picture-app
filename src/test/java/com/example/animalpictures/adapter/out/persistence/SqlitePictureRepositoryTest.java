package com.example.animalpictures.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Image;
import com.example.animalpictures.domain.ImageOrigin;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureId;
import com.example.animalpictures.domain.PictureSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Runs against a real SQLite file migrated with the production Flyway migrations. */
class SqlitePictureRepositoryTest {

    @TempDir
    Path tempDir;

    private SqlitePictureRepository repository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource("jdbc:sqlite:" + tempDir.resolve("test.db"), true);
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = new SqlitePictureRepository(jdbc);
    }

    @Test
    void savesAndRestoresThePictureWithItsDomainId() {
        Instant fetchedAt = Instant.parse("2026-10-19T06:10:20.705582087Z");
        Picture picture = picture(AnimalType.BEAR, new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3}, fetchedAt);

        repository.saveAll(List.of(picture));

        Picture loaded = repository.findById(picture.id()).orElseThrow();
        assertThat(loaded).isEqualTo(picture); // same identity
        assertThat(loaded.id()).isEqualTo(picture.id());
        assertThat(loaded.image()).isEqualTo(picture.image());
        assertThat(loaded.origin()).isEqualTo(new ImageOrigin("placebear.com", "https://placebear.com/300/200"));
        assertThat(loaded.fetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void storesTheDomainIdNotTheRowId() {
        Picture picture = picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z"));
        repository.saveAll(List.of(picture));

        String storedId = jdbc.sql("SELECT picture_id FROM picture").query(String.class).single();
        assertThat(storedId).isEqualTo(picture.id().toString());
    }

    @Test
    void latestIsOrderedByFetchTimeNotInsertOrder() {
        Picture newest = picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T10:00:00.5Z"));
        repository.saveAll(List.of(
                picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")),
                newest,
                picture(AnimalType.BEAR, new byte[] {3}, Instant.parse("2026-10-19T09:59:59.999Z"))));

        // 10:00:00 vs 10:00:00.5 would sort wrongly with Instant.toString(); fixed-width timestamps sort correctly.
        assertThat(repository.findLatest()).contains(newest);
    }

    @Test
    void latestByAnimalOnlyConsidersThatAnimal() {
        Picture olderCat = picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z"));
        repository.saveAll(List.of(olderCat, picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T11:00:00Z"))));

        assertThat(repository.findLatestByAnimalType(AnimalType.CAT)).contains(olderCat);
        assertThat(repository.findLatestByAnimalType(AnimalType.BEAR)).isEmpty();
    }

    @Test
    void sameFetchTimeFallsBackToInsertionOrder() {
        Instant same = Instant.parse("2026-10-19T10:00:00Z");
        Picture second = picture(AnimalType.CAT, new byte[] {2}, same);
        repository.saveAll(List.of(picture(AnimalType.CAT, new byte[] {1}, same), second));

        assertThat(repository.findLatest()).contains(second);
    }

    @Test
    void findRecentReturnsNewestFirstWithSizeAndLimit() {
        Picture dog = picture(AnimalType.DOG, new byte[] {1, 2, 3}, Instant.parse("2026-10-19T12:00:00Z"));
        Picture bear = picture(AnimalType.BEAR, new byte[] {1, 2}, Instant.parse("2026-10-19T11:00:00Z"));
        repository.saveAll(List.of(picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")), dog, bear));

        List<PictureSummary> recent = repository.findRecent(null, 2);

        assertThat(recent).extracting(PictureSummary::id).containsExactly(dog.id(), bear.id());
        assertThat(recent.get(0).sizeBytes()).isEqualTo(3);
        assertThat(recent.get(0).fetchedAt()).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    void findRecentFiltersByAnimal() {
        Picture cat = picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z"));
        repository.saveAll(List.of(cat, picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T11:00:00Z"))));

        assertThat(repository.findRecent(AnimalType.CAT, 10)).extracting(PictureSummary::id).containsExactly(cat.id());
        assertThat(repository.findRecent(AnimalType.BEAR, 10)).isEmpty();
    }

    @Test
    void unknownIdIsEmptyAndEmptyTableHasNoLatest() {
        assertThat(repository.findById(PictureId.newId())).isEmpty();
        assertThat(repository.findLatest()).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void sameDomainIdCannotBeStoredTwice() {
        Picture picture = picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z"));
        repository.saveAll(List.of(picture));

        assertThatThrownBy(() -> repository.saveAll(List.of(picture)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    void schemaRejectsUnknownAnimalType() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO picture (picture_id, animal_type, image, content_type, source_website, source_url, created_at)
                        VALUES ('11111111-1111-4111-8111-111111111111', 'UNICORN', x'00', 'image/jpeg', 'example.com',
                                'https://example.com', '2026-10-19T10:00:00.000000000Z')""")
                .update())
                // Spring doesn't map SQLite error codes to specific exception types, so check the message.
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CHECK constraint failed");
    }

    private static Picture picture(AnimalType type, byte[] bytes, Instant fetchedAt) {
        ImageOrigin origin = type == AnimalType.BEAR
                ? new ImageOrigin("placebear.com", "https://placebear.com/300/200")
                : new ImageOrigin("example.com", "https://example.com/1");
        return Picture.fetch(type, new Image(bytes, "image/jpeg"), origin, fetchedAt);
    }
}
