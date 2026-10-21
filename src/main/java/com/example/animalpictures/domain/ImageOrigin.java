package com.example.animalpictures.domain;

import java.util.Objects;

/**
 * Value object: where a picture came from.
 *
 * @param website the source's website, e.g. {@code placebear.com} or {@code api.inaturalist.org}
 * @param url     the exact URL the image was downloaded from
 */
public record ImageOrigin(String website, String url) {

    public ImageOrigin {
        Objects.requireNonNull(website, "origin website is required");
        Objects.requireNonNull(url, "origin url is required");
        if (website.isBlank() || url.isBlank()) {
            throw new IllegalArgumentException("origin website and url must not be blank");
        }
    }
}
