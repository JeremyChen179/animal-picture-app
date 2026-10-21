package com.example.animalpictures.domain;

import java.time.Instant;

/** Read model for listings: a picture's details without its image bytes. */
public record PictureSummary(PictureId id, AnimalType animalType, String contentType, int sizeBytes,
                             ImageOrigin origin, Instant fetchedAt) {
}
