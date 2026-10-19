package com.example.animalpictures.domain;

import java.time.Instant;

/** A stored picture without its image bytes, for listings. */
public record PictureSummary(long id, AnimalType animalType, String contentType, int sizeBytes,
                             String sourceWebsite, String sourceUrl, Instant createdAt) {
}
