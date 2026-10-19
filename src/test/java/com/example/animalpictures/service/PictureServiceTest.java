package com.example.animalpictures.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.animalpictures.client.AnimalPictureClient;
import com.example.animalpictures.client.FetchedImage;
import com.example.animalpictures.client.PictureSourceException;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PictureServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Mock
    private AnimalPictureClient client;
    @Mock
    private PictureRepository repository;

    private PictureService service;

    @BeforeEach
    void setUp() {
        service = new PictureService(client, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fetchesRequestedNumberOfPicturesAndSavesThem() {
        when(client.fetchRandom(AnimalType.DOG))
                .thenReturn(new FetchedImage(new byte[] {1}, "image/jpeg", "https://place.dog/300/200"))
                .thenReturn(new FetchedImage(new byte[] {2}, "image/jpeg", "https://place.dog/400/250"));
        AtomicLong ids = new AtomicLong();
        when(repository.save(any())).thenAnswer(invocation ->
                invocation.<Picture>getArgument(0).withId(ids.incrementAndGet()));

        List<Picture> saved = service.fetchAndSave(AnimalType.DOG, 2);

        assertThat(saved).extracting(Picture::id).containsExactly(1L, 2L);
        assertThat(saved).extracting(Picture::sourceUrl)
                .containsExactly("https://place.dog/300/200", "https://place.dog/400/250");
        assertThat(saved).allSatisfy(picture -> {
            assertThat(picture.animalType()).isEqualTo(AnimalType.DOG);
            assertThat(picture.sourceWebsite()).isEqualTo("place.dog");
            assertThat(picture.createdAt()).isEqualTo(NOW);
        });
        verify(client, times(2)).fetchRandom(AnimalType.DOG);
    }

    @Test
    void savesNothingWhenAnUpstreamCallFails() {
        when(client.fetchRandom(AnimalType.CAT))
                .thenReturn(new FetchedImage(new byte[] {1}, "image/jpeg", "https://cataas.com/cat"))
                .thenThrow(new PictureSourceException("down"));

        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.CAT, 3)).isInstanceOf(PictureSourceException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void extractsWebsiteFromUrl() {
        assertThat(PictureService.websiteOf("https://cataas.com/cat?width=300&height=200")).isEqualTo("cataas.com");
        assertThat(PictureService.websiteOf("https://placebear.com/300/200")).isEqualTo("placebear.com");
    }
}
