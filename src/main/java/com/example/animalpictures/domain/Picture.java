package com.example.animalpictures.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity (and aggregate root): a picture of an animal fetched from a public source.
 *
 * <p>Identity is a {@link PictureId} assigned by the domain when the picture is created, not by the
 * database. Two pictures are the same picture if and only if they have the same id.
 */
public final class Picture {

    private final PictureId id;
    private final AnimalType animalType;
    private final Image image;
    private final ImageOrigin origin;
    private final Instant fetchedAt;

    private Picture(PictureId id, AnimalType animalType, Image image, ImageOrigin origin, Instant fetchedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.animalType = Objects.requireNonNull(animalType, "animal type is required");
        this.image = Objects.requireNonNull(image, "image is required");
        this.origin = Objects.requireNonNull(origin, "origin is required");
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt is required");
    }

    /** A newly fetched picture, with a new identity. */
    public static Picture fetch(AnimalType animalType, Image image, ImageOrigin origin, Instant fetchedAt) {
        return new Picture(PictureId.newId(), animalType, image, origin, fetchedAt);
    }

    /** Rebuilds an existing picture with its existing identity, e.g. when a repository loads it. */
    public static Picture restore(PictureId id, AnimalType animalType, Image image, ImageOrigin origin, Instant fetchedAt) {
        return new Picture(id, animalType, image, origin, fetchedAt);
    }

    public PictureId id() {
        return id;
    }

    public AnimalType animalType() {
        return animalType;
    }

    public Image image() {
        return image;
    }

    public ImageOrigin origin() {
        return origin;
    }

    /** When the picture was fetched; the latest picture is the most recently fetched one. */
    public Instant fetchedAt() {
        return fetchedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Picture picture && id.equals(picture.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Picture[" + id + ", " + animalType + ", " + image + ", from " + origin.website() + ", fetched " + fetchedAt + "]";
    }
}
