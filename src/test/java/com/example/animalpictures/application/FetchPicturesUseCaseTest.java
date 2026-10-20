package com.example.animalpictures.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSourceException;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure unit tests: the use case with in-memory fakes for its ports, no Spring and no mocks. */
class FetchPicturesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final InMemoryPictureStore store = new InMemoryPictureStore();

    private FetchPicturesUseCase useCase(FakePictureSource source) {
        return new FetchPicturesUseCase(source, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fetchesRequestedNumberOfPicturesAndSavesThem() {
        FakePictureSource source = new FakePictureSource(
                new FetchedImage(new byte[] {1}, "image/jpeg", "https://place.dog/300/200", "place.dog"),
                new FetchedImage(new byte[] {2}, "image/jpeg", "https://place.dog/400/250", "place.dog"));

        List<Picture> saved = useCase(source).fetchAndSave(AnimalType.DOG, 2);

        assertThat(saved).extracting(Picture::id).containsExactly(1L, 2L);
        assertThat(saved).extracting(Picture::sourceUrl)
                .containsExactly("https://place.dog/300/200", "https://place.dog/400/250");
        assertThat(saved).allSatisfy(picture -> {
            assertThat(picture.animalType()).isEqualTo(AnimalType.DOG);
            assertThat(picture.sourceWebsite()).isEqualTo("place.dog");
            assertThat(picture.createdAt()).isEqualTo(NOW);
        });
        assertThat(source.calls).isEqualTo(2);
        assertThat(store.saved()).hasSize(2);
    }

    @Test
    void savesNothingWhenAnUpstreamCallFails() {
        FakePictureSource source = new FakePictureSource(
                new FetchedImage(new byte[] {1}, "image/jpeg", "https://cataas.com/cat", "cataas.com"));
        source.failAfter = 1;

        assertThatThrownBy(() -> useCase(source).fetchAndSave(AnimalType.CAT, 3)).isInstanceOf(PictureSourceException.class);

        assertThat(store.saved()).isEmpty();
    }

    /** The use case enforces its own limits, so no caller can trigger a huge allocation or thousands of downloads. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void rejectsCountOutsideOneToTenWithoutFetching(int count) {
        FakePictureSource source = new FakePictureSource();

        assertThatThrownBy(() -> useCase(source).fetchAndSave(AnimalType.BEAR, count))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be between 1 and 10, was " + count);
        assertThat(source.calls).isZero();
    }

    @Test
    void acceptsBoundaryCounts() {
        FetchedImage image = new FetchedImage(new byte[] {1}, "image/jpeg", "https://placebear.com/300/200", "placebear.com");
        assertThat(useCase(new FakePictureSource(image)).fetchAndSave(AnimalType.BEAR, 1)).hasSize(1);
        assertThat(useCase(new FakePictureSource(image)).fetchAndSave(AnimalType.BEAR, 10)).hasSize(10);
    }

    @Test
    void requiresAnimalType() {
        assertThatThrownBy(() -> useCase(new FakePictureSource()).fetchAndSave(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("animalType is required");
    }
}
