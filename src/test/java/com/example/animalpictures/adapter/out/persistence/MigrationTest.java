package com.example.animalpictures.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * A database created by the previous version (old schema, no Flyway history) must be upgraded in
 * place with the same settings as application.yml: baselined at V1, then V2 applied.
 */
class MigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void upgradesAnOldDatabaseAndGivesExistingRowsADomainId() {
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource("jdbc:sqlite:" + tempDir.resolve("old.db"), true);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        // The schema and data as the previous version left them (former schema.sql).
        jdbc.sql("""
                CREATE TABLE picture (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    animal_type TEXT NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
                    image BLOB NOT NULL, content_type TEXT NOT NULL, source_website TEXT NOT NULL,
                    source_url TEXT NOT NULL, created_at TEXT NOT NULL)""").update();
        for (int i = 1; i <= 3; i++) {
            jdbc.sql("INSERT INTO picture (animal_type, image, content_type, source_website, source_url, created_at) "
                    + "VALUES ('CAT', x'01', 'image/jpeg', 'cataas.com', 'https://cataas.com/cat', '2026-10-19T10:00:0" + i + ".000000000Z')").update();
        }

        Flyway.configure().dataSource(dataSource).baselineOnMigrate(true).baselineVersion("1").load().migrate();

        List<String> ids = jdbc.sql("SELECT picture_id FROM picture ORDER BY id").query(String.class).list();
        assertThat(ids).hasSize(3).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        // Existing data is kept, and the repository can read it.
        SqlitePictureRepository repository = new SqlitePictureRepository(jdbc);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findLatest()).get().satisfies(picture ->
                assertThat(picture.origin().website()).isEqualTo("cataas.com"));
    }
}
