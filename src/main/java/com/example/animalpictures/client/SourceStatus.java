package com.example.animalpictures.client;

import com.example.animalpictures.domain.AnimalType;

/**
 * Current circuit-breaker state of one picture source.
 *
 * @param position 0 = primary, 1 = first backup, ...
 * @param state    CLOSED (in use), OPEN (temporarily skipped) or HALF_OPEN (next call is a trial)
 */
public record SourceStatus(AnimalType animalType, int position, String website, String url, String state,
                           int bufferedCalls, int failedCalls) {
}
