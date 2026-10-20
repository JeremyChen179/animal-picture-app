package com.example.animalpictures.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PictureQueryUseCaseTest {

    private final InMemoryPictureStore store = new InMemoryPictureStore();
    private final PictureQueryUseCase queries = new PictureQueryUseCase(store);

    @Test
    void latestOverallAndPerAnimal() {
        store.saveAll(List.of(
                picture(AnimalType.CAT, "2026-10-19T10:00:00Z"),
                picture(AnimalType.DOG, "2026-10-19T11:00:00Z")));

        assertThat(queries.findLatest()).get().extracting(Picture::animalType).isEqualTo(AnimalType.DOG);
        assertThat(queries.findLatest(AnimalType.CAT)).get().extracting(Picture::animalType).isEqualTo(AnimalType.CAT);
        assertThat(queries.findLatest(AnimalType.BEAR)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 51, Integer.MAX_VALUE})
    void rejectsLimitOutsideOneToFifty(int limit) {
        assertThatThrownBy(() -> queries.findRecent(null, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 50, was " + limit);
    }

    private static Picture picture(AnimalType type, String createdAt) {
        return new Picture(null, type, new byte[] {1}, "image/jpeg", "example.com", "https://example.com/1", Instant.parse(createdAt));
    }
}
