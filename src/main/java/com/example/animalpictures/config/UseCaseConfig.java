package com.example.animalpictures.config;

import com.example.animalpictures.application.FetchPicturesUseCase;
import com.example.animalpictures.application.PictureQueryUseCase;
import com.example.animalpictures.application.SourceStatusUseCase;
import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.domain.PictureRepository;
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
    FetchPicturesUseCase fetchPicturesUseCase(PictureSource pictureSource, PictureRepository pictureRepository, Clock clock) {
        return new FetchPicturesUseCase(pictureSource, pictureRepository, clock);
    }

    @Bean
    PictureQueryUseCase pictureQueryUseCase(PictureRepository pictureRepository) {
        return new PictureQueryUseCase(pictureRepository);
    }

    @Bean
    SourceStatusUseCase sourceStatusUseCase(PictureSource pictureSource) {
        return new SourceStatusUseCase(pictureSource);
    }
}
