package com.example.animalpictures.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Runs against a real SQLite file created from the production schema.sql. */
class PictureRepositoryTest {

    @TempDir
    Path tempDir;

    private PictureRepository repository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource("jdbc:sqlite:" + tempDir.resolve("test.db"), true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        repository = new PictureRepository(jdbc);
    }

    @Test
    void savesAndReadsBackAllColumns() {
        byte[] image = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        Instant fetchedAt = Instant.parse("2026-10-19T06:10:20.705582087Z");

        Picture saved = repository.save(picture(AnimalType.BEAR, image, fetchedAt));

        assertThat(saved.id()).isPositive();
        Picture loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded.animalType()).isEqualTo(AnimalType.BEAR);
        assertThat(loaded.image()).isEqualTo(image);
        assertThat(loaded.contentType()).isEqualTo("image/jpeg");
        assertThat(loaded.sourceWebsite()).isEqualTo("placebear.com");
        assertThat(loaded.sourceUrl()).isEqualTo("https://placebear.com/300/200");
        assertThat(loaded.createdAt()).isEqualTo(fetchedAt);
    }

    @Test
    void latestIsOrderedByTimestampNotInsertOrder() {
        repository.save(picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")));
        Picture newest = repository.save(picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T10:00:00.5Z")));
        repository.save(picture(AnimalType.BEAR, new byte[] {3}, Instant.parse("2026-10-19T09:59:59.999Z")));

        // 10:00:00 vs 10:00:00.5 would sort wrongly with Instant.toString(); fixed-width timestamps sort correctly.
        assertThat(repository.findLatest()).get().extracting(Picture::id).isEqualTo(newest.id());
    }

    @Test
    void latestByAnimalOnlyConsidersThatAnimal() {
        Picture olderCat = repository.save(picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")));
        repository.save(picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T11:00:00Z")));

        assertThat(repository.findLatestByAnimalType(AnimalType.CAT)).get().extracting(Picture::id).isEqualTo(olderCat.id());
        assertThat(repository.findLatestByAnimalType(AnimalType.BEAR)).isEmpty();
    }

    @Test
    void sameTimestampFallsBackToHighestId() {
        Instant same = Instant.parse("2026-10-19T10:00:00Z");
        repository.save(picture(AnimalType.CAT, new byte[] {1}, same));
        Picture second = repository.save(picture(AnimalType.CAT, new byte[] {2}, same));

        assertThat(repository.findLatest()).get().extracting(Picture::id).isEqualTo(second.id());
    }

    @Test
    void findRecentReturnsNewestFirstWithSizeAndLimit() {
        repository.save(picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")));
        Picture dog = repository.save(picture(AnimalType.DOG, new byte[] {1, 2, 3}, Instant.parse("2026-10-19T12:00:00Z")));
        Picture bear = repository.save(picture(AnimalType.BEAR, new byte[] {1, 2}, Instant.parse("2026-10-19T11:00:00Z")));

        List<PictureSummary> recent = repository.findRecent(null, 2);

        assertThat(recent).extracting(PictureSummary::id).containsExactly(dog.id(), bear.id());
        assertThat(recent.get(0).sizeBytes()).isEqualTo(3);
        assertThat(recent.get(0).createdAt()).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    void findRecentFiltersByAnimal() {
        Picture cat = repository.save(picture(AnimalType.CAT, new byte[] {1}, Instant.parse("2026-10-19T10:00:00Z")));
        repository.save(picture(AnimalType.DOG, new byte[] {2}, Instant.parse("2026-10-19T11:00:00Z")));

        assertThat(repository.findRecent(AnimalType.CAT, 10)).extracting(PictureSummary::id).containsExactly(cat.id());
        assertThat(repository.findRecent(AnimalType.BEAR, 10)).isEmpty();
    }

    @Test
    void emptyTableHasNoLatest() {
        assertThat(repository.findLatest()).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void schemaRejectsUnknownAnimalType() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO picture (animal_type, image, content_type, source_website, source_url, created_at)
                        VALUES ('UNICORN', x'00', 'image/jpeg', 'example.com', 'https://example.com', '2026-10-19T10:00:00.000000000Z')""")
                .update())
                // Spring doesn't map SQLite error codes to specific exception types, so check the message.
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CHECK constraint failed");
    }

    private static Picture picture(AnimalType type, byte[] image, Instant createdAt) {
        String website = type == AnimalType.BEAR ? "placebear.com" : "example.com";
        String url = type == AnimalType.BEAR ? "https://placebear.com/300/200" : "https://example.com/1";
        return new Picture(null, type, image, "image/jpeg", website, url, createdAt);
    }
}
