package com.example.animalpictures.application;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureId;
import com.example.animalpictures.domain.PictureRepository;
import com.example.animalpictures.domain.PictureSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Test double for the domain's PictureRepository. Pictures already carry their identity. */
class InMemoryPictureRepository implements PictureRepository {

    private final List<Picture> pictures = new ArrayList<>();

    List<Picture> stored() {
        return pictures;
    }

    @Override
    public void saveAll(List<Picture> toSave) {
        pictures.addAll(toSave);
    }

    /** Newest first; insertion order breaks ties (like the database's row order). */
    private List<Picture> newestFirst() {
        List<Picture> sorted = new ArrayList<>(pictures);
        java.util.Collections.reverse(sorted);
        sorted.sort(Comparator.comparing(Picture::fetchedAt).reversed());
        return sorted;
    }

    @Override
    public Optional<Picture> findLatest() {
        return newestFirst().stream().findFirst();
    }

    @Override
    public Optional<Picture> findLatestByAnimalType(AnimalType animalType) {
        return newestFirst().stream().filter(p -> p.animalType() == animalType).findFirst();
    }

    @Override
    public Optional<Picture> findById(PictureId id) {
        return pictures.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public List<PictureSummary> findRecent(AnimalType animalType, int limit) {
        return newestFirst().stream()
                .filter(p -> animalType == null || p.animalType() == animalType)
                .limit(limit)
                .map(p -> new PictureSummary(p.id(), p.animalType(), p.image().contentType(), p.image().sizeBytes(),
                        p.origin(), p.fetchedAt()))
                .toList();
    }
}
