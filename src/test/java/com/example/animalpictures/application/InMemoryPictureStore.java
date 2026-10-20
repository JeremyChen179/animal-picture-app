package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.PictureStore;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Test double for the PictureStore port. */
class InMemoryPictureStore implements PictureStore {

    private final List<Picture> pictures = new ArrayList<>();
    private long nextId = 1;

    List<Picture> saved() {
        return pictures;
    }

    @Override
    public List<Picture> saveAll(List<Picture> toSave) {
        List<Picture> withIds = toSave.stream().map(picture -> picture.withId(nextId++)).toList();
        pictures.addAll(withIds);
        return withIds;
    }

    private static final Comparator<Picture> NEWEST_FIRST =
            Comparator.comparing(Picture::createdAt).thenComparing(Picture::id).reversed();

    @Override
    public Optional<Picture> findLatest() {
        return pictures.stream().sorted(NEWEST_FIRST).findFirst();
    }

    @Override
    public Optional<Picture> findLatestByAnimalType(AnimalType animalType) {
        return pictures.stream().filter(p -> p.animalType() == animalType).sorted(NEWEST_FIRST).findFirst();
    }

    @Override
    public Optional<Picture> findById(long id) {
        return pictures.stream().filter(p -> p.id() == id).findFirst();
    }

    @Override
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        return pictures.stream()
                .filter(p -> animalType == null || p.animalType() == animalType)
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .map(p -> new PictureSummary(p.id(), p.animalType(), p.contentType(), p.image().length,
                        p.sourceWebsite(), p.sourceUrl(), p.createdAt()))
                .toList();
    }
}
