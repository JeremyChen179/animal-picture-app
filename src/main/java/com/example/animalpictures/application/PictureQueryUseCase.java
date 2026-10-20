package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.PictureStore;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureSummary;
import java.util.List;
import java.util.Optional;

/** Use case b (and friends): read stored pictures. */
public class PictureQueryUseCase {

    public static final int MAX_RECENT = 50;

    private final PictureStore pictureStore;

    public PictureQueryUseCase(PictureStore pictureStore) {
        this.pictureStore = pictureStore;
    }

    /** The latest stored picture of any animal. */
    public Optional<Picture> findLatest() {
        return pictureStore.findLatest();
    }

    public Optional<Picture> findLatest(AnimalType animalType) {
        return pictureStore.findLatestByAnimalType(animalType);
    }

    public Optional<Picture> findById(long id) {
        return pictureStore.findById(id);
    }

    /** Most recent pictures first; {@code animalType} may be null for all animals. */
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        if (limit < 1 || limit > MAX_RECENT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_RECENT + ", was " + limit);
        }
        return pictureStore.findRecent(animalType, limit);
    }
}
