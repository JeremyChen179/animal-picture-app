package com.example.animalpictures.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PictureTest {

    private static final Image IMAGE = new Image(new byte[] {1, 2, 3}, "image/jpeg");
    private static final ImageOrigin ORIGIN = new ImageOrigin("placebear.com", "https://placebear.com/300/200");
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void fetchedPictureHasItsIdentityFromTheStart() {
        Picture first = Picture.fetch(AnimalType.BEAR, IMAGE, ORIGIN, NOW);
        Picture second = Picture.fetch(AnimalType.BEAR, IMAGE, ORIGIN, NOW);

        assertThat(first.id()).isNotNull();
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void picturesAreEqualByIdentityOnly() {
        Picture picture = Picture.fetch(AnimalType.BEAR, IMAGE, ORIGIN, NOW);
        Picture sameIdDifferentData = Picture.restore(picture.id(), AnimalType.CAT,
                new Image(new byte[] {9}, "image/png"), new ImageOrigin("cataas.com", "https://cataas.com/cat"), NOW.plusSeconds(5));
        Picture sameDataDifferentId = Picture.fetch(AnimalType.BEAR, IMAGE, ORIGIN, NOW);

        assertThat(picture).isEqualTo(sameIdDifferentData).hasSameHashCodeAs(sameIdDifferentData);
        assertThat(picture).isNotEqualTo(sameDataDifferentId);
    }

    @Test
    void restoreKeepsTheGivenIdentity() {
        PictureId id = new PictureId(UUID.fromString("3f2a8c1e-5b7d-4e9a-8c3f-1a2b3c4d5e6f"));

        assertThat(Picture.restore(id, AnimalType.DOG, IMAGE, ORIGIN, NOW).id()).isEqualTo(id);
    }

    @Test
    void requiresAllParts() {
        assertThatThrownBy(() -> Picture.fetch(null, IMAGE, ORIGIN, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Picture.fetch(AnimalType.CAT, null, ORIGIN, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Picture.restore(null, AnimalType.CAT, IMAGE, ORIGIN, NOW)).isInstanceOf(NullPointerException.class);
    }
}
