package com.example.animalpictures.domain;

import java.time.Instant;

/**
 * A stored picture; mirrors one row of the {@code picture} table (see {@code schema.sql}).
 *
 * @param id            database id, {@code null} until saved
 * @param sourceWebsite host the picture came from, e.g. {@code placebear.com}
 * @param sourceUrl     full URL it was downloaded from
 * @param createdAt     when it was fetched; used to find the latest picture
 */
public record Picture(Long id, AnimalType animalType, byte[] image, String contentType,
                      String sourceWebsite, String sourceUrl, Instant createdAt) {

    public Picture withId(long newId) {
        return new Picture(newId, animalType, image, contentType, sourceWebsite, sourceUrl, createdAt);
    }
}
