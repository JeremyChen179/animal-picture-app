package com.example.animalpictures.application.port.out;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureSummary;
import java.util.List;
import java.util.Optional;

/** Outbound port: where pictures are stored (implemented by the SQLite adapter). */
public interface PictureStore {

    /** Saves all pictures atomically: either all are stored or none. Returns them with their ids. */
    List<Picture> saveAll(List<Picture> pictures);

    /** Latest picture of any animal. */
    Optional<Picture> findLatest();

    Optional<Picture> findLatestByAnimalType(AnimalType animalType);

    Optional<Picture> findById(long id);

    /** Most recent pictures first, without image bytes; {@code animalType} may be null for all animals. */
    List<PictureSummary> findRecent(AnimalType animalType, int limit);
}
