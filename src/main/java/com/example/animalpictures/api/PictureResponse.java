package com.example.animalpictures.api;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureSummary;
import java.time.Instant;

/** Metadata about a stored picture; the image itself is served from {@code imageUrl}. */
public record PictureResponse(long id, AnimalType animalType, String contentType, int sizeBytes,
                              String sourceWebsite, String sourceUrl, Instant createdAt, String imageUrl) {

    static PictureResponse from(Picture picture) {
        return new PictureResponse(picture.id(), picture.animalType(), picture.contentType(),
                picture.image().length, picture.sourceWebsite(), picture.sourceUrl(), picture.createdAt(),
                "/api/pictures/" + picture.id() + "/image");
    }

    static PictureResponse from(PictureSummary summary) {
        return new PictureResponse(summary.id(), summary.animalType(), summary.contentType(), summary.sizeBytes(),
                summary.sourceWebsite(), summary.sourceUrl(), summary.createdAt(),
                "/api/pictures/" + summary.id() + "/image");
    }
}
