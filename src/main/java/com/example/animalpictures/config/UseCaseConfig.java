package com.example.animalpictures.config;

import com.example.animalpictures.application.FetchPicturesUseCase;
import com.example.animalpictures.application.PictureQueryUseCase;
import com.example.animalpictures.application.SourceStatusUseCase;
import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.PictureStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free use cases to the adapters that implement their ports.
 * This is the only place that knows both sides.
 */
@Configuration
class UseCaseConfig {

    @Bean
    FetchPicturesUseCase fetchPicturesUseCase(PictureSource pictureSource, PictureStore pictureStore, Clock clock) {
        return new FetchPicturesUseCase(pictureSource, pictureStore, clock);
    }

    @Bean
    PictureQueryUseCase pictureQueryUseCase(PictureStore pictureStore) {
        return new PictureQueryUseCase(pictureStore);
    }

    @Bean
    SourceStatusUseCase sourceStatusUseCase(PictureSource pictureSource) {
        return new SourceStatusUseCase(pictureSource);
    }
}
