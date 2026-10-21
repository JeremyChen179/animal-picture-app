package com.example.animalpictures.application;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureId;
import com.example.animalpictures.domain.PictureRepository;
import com.example.animalpictures.domain.PictureSummary;
import java.util.List;
import java.util.Optional;

/** Use case b (and friends): read stored pictures. */
public class PictureQueryUseCase {

    public static final int MAX_RECENT = 50;

    private final PictureRepository pictureRepository;

    public PictureQueryUseCase(PictureRepository pictureRepository) {
        this.pictureRepository = pictureRepository;
    }

    /** The most recently fetched picture of any animal. */
    public Optional<Picture> findLatest() {
        return pictureRepository.findLatest();
    }

    public Optional<Picture> findLatest(AnimalType animalType) {
        return pictureRepository.findLatestByAnimalType(animalType);
    }

    public Optional<Picture> findById(PictureId id) {
        return pictureRepository.findById(id);
    }

    /** Most recently fetched first; {@code animalType} may be null for all animals. */
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        if (limit < 1 || limit > MAX_RECENT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_RECENT + ", was " + limit);
        }
        return pictureRepository.findRecent(animalType, limit);
    }
}
