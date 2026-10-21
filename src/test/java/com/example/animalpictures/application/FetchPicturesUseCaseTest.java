package com.example.animalpictures.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSourceException;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Image;
import com.example.animalpictures.domain.ImageOrigin;
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

    private final InMemoryPictureRepository repository = new InMemoryPictureRepository();

    private FetchPicturesUseCase useCase(FakePictureSource source) {
        return new FetchPicturesUseCase(source, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fetchesRequestedNumberOfPicturesAndSavesThem() {
        FakePictureSource source = new FakePictureSource(
                new FetchedImage(new Image(new byte[] {1}, "image/jpeg"), new ImageOrigin("place.dog", "https://place.dog/300/200")),
                new FetchedImage(new Image(new byte[] {2}, "image/jpeg"), new ImageOrigin("place.dog", "https://place.dog/400/250")));

        List<Picture> saved = useCase(source).fetchAndSave(AnimalType.DOG, 2);

        assertThat(saved).extracting(picture -> picture.origin().url())
                .containsExactly("https://place.dog/300/200", "https://place.dog/400/250");
        assertThat(saved).allSatisfy(picture -> {
            assertThat(picture.animalType()).isEqualTo(AnimalType.DOG);
            assertThat(picture.origin().website()).isEqualTo("place.dog");
            assertThat(picture.fetchedAt()).isEqualTo(NOW);
        });
        assertThat(source.calls).isEqualTo(2);
        // What was returned is exactly what was stored: the identity didn't change on save.
        assertThat(repository.stored()).containsExactlyElementsOf(saved);
    }

    @Test
    void eachPictureGetsItsOwnDomainIdBeforeItIsStored() {
        FakePictureSource source = new FakePictureSource(
                new FetchedImage(new Image(new byte[] {1}, "image/jpeg"), new ImageOrigin("place.dog", "https://place.dog/300/200")));

        List<Picture> saved = useCase(source).fetchAndSave(AnimalType.DOG, 3);

        assertThat(saved).extracting(Picture::id).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(repository.findById(saved.get(1).id())).contains(saved.get(1));
    }

    @Test
    void savesNothingWhenAnUpstreamCallFails() {
        FakePictureSource source = new FakePictureSource(
                new FetchedImage(new Image(new byte[] {1}, "image/jpeg"), new ImageOrigin("cataas.com", "https://cataas.com/cat")));
        source.failAfter = 1;

        assertThatThrownBy(() -> useCase(source).fetchAndSave(AnimalType.CAT, 3)).isInstanceOf(PictureSourceException.class);

        assertThat(repository.stored()).isEmpty();
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
        FetchedImage image = new FetchedImage(new Image(new byte[] {1}, "image/jpeg"), new ImageOrigin("placebear.com", "https://placebear.com/300/200"));
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
