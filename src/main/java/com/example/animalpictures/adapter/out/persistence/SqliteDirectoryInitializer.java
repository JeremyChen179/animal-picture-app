package com.example.animalpictures.adapter.out.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * The SQLite driver creates the database file on first connect, but not its parent directory.
 * This creates the directory (e.g. {@code ./data}) before the DataSource starts.
 * Registered in {@code META-INF/spring.factories} so it also runs in tests.
 */
public class SqliteDirectoryInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String PREFIX = "jdbc:sqlite:";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String url = event.getEnvironment().getProperty("spring.datasource.url");
        if (url == null || !url.startsWith(PREFIX)) {
            return;
        }
        String file = url.substring(PREFIX.length());
        int query = file.indexOf('?');
        if (query >= 0) {
            file = file.substring(0, query);
        }
        if (file.isBlank() || file.startsWith(":memory:") || file.startsWith("file:")) {
            return;
        }
        Path parent = Path.of(file).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create directory for SQLite database: " + parent, e);
        }
    }
}
