package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.PictureStore;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use case a: fetch {@code count} random pictures of an animal and store them.
 *
 * <p>All pictures are downloaded first and only then saved, atomically, so a failing source never
 * leaves a partial batch. Downloads deliberately happen outside any database transaction: they can
 * take many seconds and must not hold the database connection.
 */
public class FetchPicturesUseCase {

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 10;

    private final PictureSource pictureSource;
    private final PictureStore pictureStore;
    private final Clock clock;

    public FetchPicturesUseCase(PictureSource pictureSource, PictureStore pictureStore, Clock clock) {
        this.pictureSource = pictureSource;
        this.pictureStore = pictureStore;
        this.clock = clock;
    }

    public List<Picture> fetchAndSave(AnimalType animalType, int count) {
        Objects.requireNonNull(animalType, "animalType is required");
        // Enforced here, not only in the web layer: any caller gets the same rule, and a huge count
        // can't trigger a huge allocation or thousands of downloads.
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "count must be between " + MIN_COUNT + " and " + MAX_COUNT + ", was " + count);
        }
        List<Picture> fetched = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FetchedImage image = pictureSource.fetchRandom(animalType);
            fetched.add(new Picture(null, animalType, image.data(), image.contentType(),
                    image.sourceWebsite(), image.sourceUrl(), Instant.now(clock)));
        }
        return pictureStore.saveAll(fetched);
    }
}
