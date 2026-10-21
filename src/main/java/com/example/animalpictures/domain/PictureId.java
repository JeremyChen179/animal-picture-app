package com.example.animalpictures.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link Picture}. Created by the domain when a picture is fetched, so a picture has
 * its identity from the start; how (or whether) it is stored doesn't affect it.
 */
public record PictureId(UUID value) {

    public PictureId {
        Objects.requireNonNull(value, "picture id value is required");
    }

    public static PictureId newId() {
        return new PictureId(UUID.randomUUID());
    }

    /** Parses the canonical UUID form, e.g. {@code 3f2a8c1e-5b7d-4e9a-8c3f-1a2b3c4d5e6f}. */
    public static PictureId of(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            // UUID.fromString also accepts non-canonical forms such as "1-1-1-1-1"; only accept canonical ids.
            if (uuid.toString().equalsIgnoreCase(value)) {
                return new PictureId(uuid);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            // fall through to a clear message
        }
        throw new IllegalArgumentException("Invalid picture id '" + value + "'");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
