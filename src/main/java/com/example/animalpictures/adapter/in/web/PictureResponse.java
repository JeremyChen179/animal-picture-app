package com.example.animalpictures.adapter.in.web;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureSummary;
import java.time.Instant;

/**
 * Metadata about a stored picture; the image itself is served from {@code imageUrl}.
 * {@code id} is the picture's domain id (a UUID), the same id used in {@code imageUrl}.
 */
public record PictureResponse(String id, AnimalType animalType, String contentType, int sizeBytes,
                              String sourceWebsite, String sourceUrl, Instant createdAt, String imageUrl) {

    static PictureResponse from(Picture picture) {
        return new PictureResponse(picture.id().toString(), picture.animalType(), picture.image().contentType(),
                picture.image().sizeBytes(), picture.origin().website(), picture.origin().url(), picture.fetchedAt(),
                imageUrl(picture.id().toString()));
    }

    static PictureResponse from(PictureSummary summary) {
        return new PictureResponse(summary.id().toString(), summary.animalType(), summary.contentType(), summary.sizeBytes(),
                summary.origin().website(), summary.origin().url(), summary.fetchedAt(), imageUrl(summary.id().toString()));
    }

    private static String imageUrl(String id) {
        return "/api/pictures/" + id + "/image";
    }
}
