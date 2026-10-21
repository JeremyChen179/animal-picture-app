package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use case a: fetch {@code count} random pictures of an animal and store them.
 *
 * <p>Each picture gets its identity from the domain ({@link Picture#fetch}) when it's created, so the
 * returned pictures are complete before and after storing. All pictures are downloaded first and
 * only then stored, atomically, so a failing source never leaves a partial batch. Downloads happen
 * outside any database transaction: they can take many seconds and must not hold the connection.
 */
public class FetchPicturesUseCase {

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 10;

    private final PictureSource pictureSource;
    private final PictureRepository pictureRepository;
    private final Clock clock;

    public FetchPicturesUseCase(PictureSource pictureSource, PictureRepository pictureRepository, Clock clock) {
        this.pictureSource = pictureSource;
        this.pictureRepository = pictureRepository;
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
        List<Picture> pictures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FetchedImage fetched = pictureSource.fetchRandom(animalType);
            pictures.add(Picture.fetch(animalType, fetched.image(), fetched.origin(), Instant.now(clock)));
        }
        pictureRepository.saveAll(pictures);
        return List.copyOf(pictures);
    }
}
