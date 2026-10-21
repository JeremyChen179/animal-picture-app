package com.example.animalpictures.application.port.out;

import com.example.animalpictures.domain.Image;
import com.example.animalpictures.domain.ImageOrigin;
import java.util.Objects;

/** What a {@link PictureSource} returns: a downloaded image and where it came from. */
public record FetchedImage(Image image, ImageOrigin origin) {

    public FetchedImage {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(origin, "origin is required");
    }
}
