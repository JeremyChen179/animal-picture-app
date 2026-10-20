package com.example.animalpictures.client;

import com.example.animalpictures.client.PictureSourceProperties.Source;
import com.example.animalpictures.client.PictureSourceProperties.CircuitBreakerSettings;
import com.example.animalpictures.domain.AnimalType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AnimalPictureClient {

    private static final Logger log = LoggerFactory.getLogger(AnimalPictureClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final PictureSourceProperties properties;
    private final CircuitBreakerRegistry circuitBreakers;

    public AnimalPictureClient(RestClient pictureRestClient, PictureSourceProperties properties) {
        this.restClient = pictureRestClient;
        this.properties = properties;
        this.circuitBreakers = CircuitBreakerRegistry.of(circuitBreakerConfig(properties.circuitBreaker()));
    }

    /**
     * Tries each configured source in order and returns the first image; fails only if all sources fail.
     * Sources whose circuit breaker is open (recently failing) are skipped without being called.
     */
    public FetchedImage fetchRandom(AnimalType animalType) {
        List<Source> sources = properties.sources().getOrDefault(animalType, List.of());
        if (sources.isEmpty()) {
            throw new PictureSourceException("No picture source configured for " + animalType);
        }
        RuntimeException lastFailure = null;
        for (int position = 0; position < sources.size(); position++) {
            Source source = sources.get(position);
            CircuitBreaker breaker = breakerFor(animalType, position, source);
            try {
                return breaker.executeSupplier(() -> fetchFrom(animalType, source));
            } catch (CallNotPermittedException e) {
                log.info("{} source {} skipped: circuit open after repeated failures", animalType, breaker.getName());
                lastFailure = new PictureSourceException(breaker.getName() + " is temporarily skipped (circuit open)");
            } catch (PictureSourceException e) {
                log.warn("{} source failed, trying next if available: {}", animalType, e.getMessage());
                lastFailure = e;
            }
        }
        throw new PictureSourceException(
                "All " + sources.size() + " " + animalType + " picture source(s) failed. Last error: "
                        + lastFailure.getMessage(), lastFailure);
    }

    /** Circuit-breaker state of every configured source, primary first. */
    public List<SourceStatus> sourceStatuses() {
        List<SourceStatus> statuses = new ArrayList<>();
        properties.sources().forEach((animalType, sources) -> {
            for (int position = 0; position < sources.size(); position++) {
                Source source = sources.get(position);
                CircuitBreaker breaker = breakerFor(animalType, position, source);
                CircuitBreaker.Metrics metrics = breaker.getMetrics();
                statuses.add(new SourceStatus(animalType, position, hostOf(source.url()), source.url(),
                        breaker.getState().name(), metrics.getNumberOfBufferedCalls(), metrics.getNumberOfFailedCalls()));
            }
        });
        statuses.sort((a, b) -> a.animalType() != b.animalType()
                ? a.animalType().compareTo(b.animalType()) : Integer.compare(a.position(), b.position()));
        return statuses;
    }

    private CircuitBreaker breakerFor(AnimalType animalType, int position, Source source) {
        return circuitBreakers.circuitBreaker(animalType + "#" + position + " " + hostOf(source.url()));
    }

    /**
     * Opens after {@code failureThreshold} failures in a row: a count-based window of that size with a
     * 100% failure-rate threshold. Stays open for {@code openDuration}, then lets one trial call through.
     */
    private static CircuitBreakerConfig circuitBreakerConfig(CircuitBreakerSettings settings) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.failureThreshold())
                .minimumNumberOfCalls(settings.failureThreshold())
                .failureRateThreshold(100)
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(PictureSourceException.class)
                .build();
    }

    private FetchedImage fetchFrom(AnimalType animalType, Source source) {
        String url = expand(source);
        String imageUrl = source.isJson() ? resolveImageUrl(animalType, source, url) : url;
        return downloadImage(animalType, imageUrl, hostOf(url));
    }

    /** Step 1 of a JSON source: call the API and pull the image URL out of the response. */
    private String resolveImageUrl(AnimalType animalType, Source source, String apiUrl) {
        Map<String, Object> json;
        try {
            json = restClient.get().uri(apiUrl).accept(MediaType.APPLICATION_JSON).retrieve().body(JSON_OBJECT);
        } catch (RestClientException e) {
            throw new PictureSourceException("Failed to fetch " + animalType + " picture metadata from " + apiUrl, e);
        }
        Object value = JsonPointer.find(json, source.imageUrlPath()).orElse(null);
        if (!(value instanceof String imageUrl) || !imageUrl.startsWith("http")) {
            throw new PictureSourceException(
                    "Upstream " + apiUrl + " returned no image URL at " + source.imageUrlPath());
        }
        if (source.imageUrlFind() != null && source.imageUrlReplace() != null) {
            imageUrl = imageUrl.replace(source.imageUrlFind(), source.imageUrlReplace());
        }
        checkAllowedImageUrl(apiUrl, source, imageUrl);
        return imageUrl;
    }

    /** The image URL comes from a third-party response, so only follow it to expected https hosts. */
    private static void checkAllowedImageUrl(String apiUrl, Source source, String imageUrl) {
        List<String> allowedHosts = source.allowedImageHosts() == null ? List.of() : source.allowedImageHosts();
        UriComponents uri = UriComponentsBuilder.fromUriString(imageUrl).build();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || allowedHosts.stream().noneMatch(host -> host.equalsIgnoreCase(uri.getHost()))) {
            throw new PictureSourceException("Upstream " + apiUrl + " returned an image URL on a host that isn't allowed: "
                    + imageUrl + " (allowed: https on " + allowedHosts + ")");
        }
    }

    /**
     * Downloads the image, reading at most {@code maxImageSize} bytes: the size is checked against
     * Content-Length first and enforced again while reading, so a huge or endless response can't
     * exhaust memory.
     */
    private FetchedImage downloadImage(AnimalType animalType, String url, String sourceWebsite) {
        long maxBytes = properties.maxImageSize().toBytes();
        try {
            return restClient.get()
                    .uri(url)
                    .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new PictureSourceException("Failed to fetch " + animalType + " picture from " + url
                                    + " (HTTP " + response.getStatusCode().value() + ")");
                        }
                        MediaType contentType = response.getHeaders().getContentType();
                        if (contentType == null || !"image".equals(contentType.getType())) {
                            throw new PictureSourceException("Upstream " + url + " did not return an image (content type: " + contentType + ")");
                        }
                        long declaredLength = response.getHeaders().getContentLength();
                        if (declaredLength > maxBytes) {
                            throw new PictureSourceException(tooLarge(url, declaredLength + " bytes", maxBytes));
                        }
                        byte[] body = readAtMost(response.getBody(), maxBytes);
                        if (body == null) {
                            throw new PictureSourceException(tooLarge(url, "more than " + maxBytes + " bytes", maxBytes));
                        }
                        if (body.length == 0) {
                            throw new PictureSourceException("Upstream " + url + " returned an empty image");
                        }
                        return new FetchedImage(body, normalize(contentType), url, sourceWebsite);
                    });
        } catch (RestClientException e) {
            throw new PictureSourceException("Failed to fetch " + animalType + " picture from " + url, e);
        }
    }

    /** Reads up to {@code maxBytes}; returns null if the stream has more. */
    private static byte[] readAtMost(InputStream body, long maxBytes) throws IOException {
        byte[] data = body.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
        return data.length > maxBytes ? null : data;
    }

    private static String tooLarge(String url, String actual, long maxBytes) {
        return "Upstream " + url + " image is too large (" + actual + "; limit " + maxBytes + " bytes)";
    }

    private String expand(Source source) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return UriComponentsBuilder.fromUriString(source.url())
                .buildAndExpand(Map.of(
                        "width", random.nextInt(properties.minSize(), properties.maxSize() + 1),
                        "height", random.nextInt(properties.minSize(), properties.maxSize() + 1),
                        "random", random.nextInt(1, source.randomMaxOrDefault() + 1)))
                .toUriString();
    }

    /** Host of a URL or URL template (templates may still contain placeholders like {width}). */
    static String hostOf(String url) {
        String host = UriComponentsBuilder.fromUriString(url).build().getHost();
        return host != null ? host : url;
    }

    /** Some sources send the non-standard {@code image/jpg}; store the standard {@code image/jpeg}. */
    private static String normalize(MediaType contentType) {
        String value = contentType.getType() + "/" + contentType.getSubtype();
        return "image/jpg".equalsIgnoreCase(value) ? MediaType.IMAGE_JPEG_VALUE : value;
    }
}
