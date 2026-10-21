package com.example.animalpictures.domain;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@link Picture} aggregate: the collection of all stored pictures.
 * Part of the domain; implemented by a persistence adapter. Pictures arrive with their identity,
 * so storing them doesn't change them.
 */
public interface PictureRepository {

    /** Stores all pictures atomically: either all are stored or none. */
    void saveAll(List<Picture> pictures);

    /** The most recently fetched picture of any animal. */
    Optional<Picture> findLatest();

    Optional<Picture> findLatestByAnimalType(AnimalType animalType);

    Optional<Picture> findById(PictureId id);

    /** Most recently fetched first; {@code animalType} may be null for all animals. */
    List<PictureSummary> findRecent(AnimalType animalType, int limit);
}
