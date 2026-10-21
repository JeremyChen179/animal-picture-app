package com.example.animalpictures.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Value object: an image's bytes and media type. Always valid: non-empty bytes with an
 * {@code image/*} content type. Immutable: the bytes are copied in and out.
 */
public final class Image {

    private final byte[] bytes;
    private final String contentType;

    public Image(byte[] bytes, String contentType) {
        Objects.requireNonNull(bytes, "image bytes are required");
        Objects.requireNonNull(contentType, "image content type is required");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image must not be empty");
        }
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("not an image content type: " + contentType);
        }
        this.bytes = bytes.clone();
        this.contentType = contentType;
    }

    /** A copy of the image bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }

    public String contentType() {
        return contentType;
    }

    public int sizeBytes() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Image image && contentType.equals(image.contentType) && Arrays.equals(bytes, image.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * contentType.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Image[" + contentType + ", " + bytes.length + " bytes]";
    }
}
