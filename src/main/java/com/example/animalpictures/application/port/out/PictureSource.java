package com.example.animalpictures.application.port.out;

import com.example.animalpictures.domain.AnimalType;
import java.util.List;

/** Outbound port: somewhere random animal pictures come from (implemented by the HTTP adapter). */
public interface PictureSource {

    /**
     * Fetches one random picture of the given animal.
     *
     * @throws PictureSourceException if no picture could be fetched
     */
    FetchedImage fetchRandom(AnimalType animalType);

    /** Health of each underlying source, primary first (e.g. circuit-breaker state). */
    List<SourceStatus> statuses();
}
