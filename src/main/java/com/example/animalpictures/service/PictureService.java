package com.example.animalpictures.service;

import com.example.animalpictures.client.AnimalPictureClient;
import com.example.animalpictures.client.FetchedImage;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PictureService {

    private final AnimalPictureClient client;
    private final PictureRepository repository;
    private final Clock clock;

    public PictureService(AnimalPictureClient client, PictureRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Downloads {@code count} pictures first and only then saves them in one transaction, so a failing
     * upstream call doesn't leave a partial batch in the database.
     */
    @Transactional
    public List<Picture> fetchAndSave(AnimalType animalType, int count) {
        List<Picture> fetched = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FetchedImage image = client.fetchRandom(animalType);
            fetched.add(new Picture(null, animalType, image.data(), image.contentType(),
                    image.sourceWebsite(), image.sourceUrl(), Instant.now(clock)));
        }
        return fetched.stream().map(repository::save).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Picture> findLatest() {
        return repository.findLatest();
    }

    @Transactional(readOnly = true)
    public Optional<Picture> findLatest(AnimalType animalType) {
        return repository.findLatestByAnimalType(animalType);
    }

    @Transactional(readOnly = true)
    public Optional<Picture> findById(long id) {
        return repository.findById(id);
    }
}
