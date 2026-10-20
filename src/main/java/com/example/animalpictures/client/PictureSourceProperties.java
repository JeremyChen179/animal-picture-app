package com.example.animalpictures.client;

import com.example.animalpictures.domain.AnimalType;
import java.time.Duration;
import java.util.List;
import org.springframework.util.unit.DataSize;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Picture sources per animal, tried in order until one returns an image.
 *
 * @param sources  ordered sources per animal
 * @param minSize  smallest random width/height substituted into {@code {width}} / {@code {height}}
 * @param maxSize  largest random width/height
 * @param circuitBreaker when to stop calling a failing source for a while
 * @param maxImageSize   largest image accepted from a source; bigger downloads are aborted
 */
@ConfigurationProperties(prefix = "pictures")
public record PictureSourceProperties(Map<AnimalType, List<Source>> sources, int minSize, int maxSize,
                                      CircuitBreakerSettings circuitBreaker, DataSize maxImageSize) {

    public static final DataSize DEFAULT_MAX_IMAGE_SIZE = DataSize.ofMegabytes(10);

    public PictureSourceProperties {
        if (circuitBreaker == null) {
            circuitBreaker = CircuitBreakerSettings.DEFAULT;
        }
        if (maxImageSize == null) {
            maxImageSize = DEFAULT_MAX_IMAGE_SIZE;
        }
    }

    /**
     * Per-source circuit breaker. After {@code failureThreshold} failures in a row the source is skipped
     * (circuit OPEN) for {@code openDuration}; then one trial call is allowed (HALF_OPEN) and a success
     * closes the circuit again.
     */
    public record CircuitBreakerSettings(int failureThreshold, Duration openDuration) {

        public static final CircuitBreakerSettings DEFAULT = new CircuitBreakerSettings(2, Duration.ofSeconds(60));
    }

    /**
     * One upstream picture API.
     *
     * <p>Two kinds are supported:
     * <ul>
     *   <li><b>Image source</b> (only {@code url} set): the URL returns the image itself,
     *       e.g. {@code https://placebear.com/{width}/{height}}.</li>
     *   <li><b>JSON source</b> ({@code imageUrlPath} set): the URL returns JSON containing the image URL,
     *       found with a JSON Pointer such as {@code /message}; the image is then downloaded from it.</li>
     * </ul>
     * URL placeholders: {@code {width}}, {@code {height}} (random size) and {@code {random}}
     * (random integer from 1 to {@code randomMax}, e.g. a random results page).
     *
     * @param url             URL template
     * @param imageUrlPath    JSON Pointer to the image URL in the response (JSON sources only)
     * @param imageUrlFind    optional text to replace in the extracted image URL (e.g. a thumbnail size)
     * @param imageUrlReplace replacement for {@code imageUrlFind}
     * @param randomMax       upper bound for {@code {random}}; defaults to 1000
     * @param allowedImageHosts hosts a JSON source's image URL may point to (https only). Required for
     *                          JSON sources, so a compromised API can't make the app fetch arbitrary
     *                          (e.g. internal) addresses
     */
    public record Source(String url, String imageUrlPath, String imageUrlFind, String imageUrlReplace,
                         Integer randomMax, List<String> allowedImageHosts) {

        public boolean isJson() {
            return imageUrlPath != null && !imageUrlPath.isBlank();
        }

        public int randomMaxOrDefault() {
            return randomMax != null && randomMax > 0 ? randomMax : 1000;
        }

        /** Convenience factory for an image source. */
        public static Source image(String url) {
            return new Source(url, null, null, null, null, null);
        }

        /** Convenience factory for a JSON source. */
        public static Source json(String url, String imageUrlPath, List<String> allowedImageHosts) {
            return new Source(url, imageUrlPath, null, null, null, allowedImageHosts);
        }
    }
}
