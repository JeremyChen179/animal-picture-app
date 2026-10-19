package com.example.animalpictures.client;

import com.example.animalpictures.client.PictureSourceProperties.Source;
import com.example.animalpictures.domain.AnimalType;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AnimalPictureClient {

    private static final Logger log = LoggerFactory.getLogger(AnimalPictureClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final PictureSourceProperties properties;

    public AnimalPictureClient(RestClient pictureRestClient, PictureSourceProperties properties) {
        this.restClient = pictureRestClient;
        this.properties = properties;
    }

    /** Tries each configured source in order and returns the first image; fails only if all sources fail. */
    public FetchedImage fetchRandom(AnimalType animalType) {
        List<Source> sources = properties.sources().getOrDefault(animalType, List.of());
        if (sources.isEmpty()) {
            throw new PictureSourceException("No picture source configured for " + animalType);
        }
        PictureSourceException lastFailure = null;
        for (Source source : sources) {
            try {
                return fetchFrom(animalType, source);
            } catch (PictureSourceException e) {
                log.warn("{} source failed, trying next if available: {}", animalType, e.getMessage());
                lastFailure = e;
            }
        }
        throw new PictureSourceException(
                "All " + sources.size() + " " + animalType + " picture source(s) failed. Last error: "
                        + lastFailure.getMessage(), lastFailure);
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
        return imageUrl;
    }

    private FetchedImage downloadImage(AnimalType animalType, String url, String sourceWebsite) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(url)
                    .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class);
            MediaType contentType = response.getHeaders().getContentType();
            byte[] body = response.getBody();
            if (contentType == null || !"image".equals(contentType.getType()) || body == null || body.length == 0) {
                throw new PictureSourceException("Upstream " + url + " did not return an image (content type: " + contentType + ")");
            }
            return new FetchedImage(body, normalize(contentType), url, sourceWebsite);
        } catch (RestClientException e) {
            throw new PictureSourceException("Failed to fetch " + animalType + " picture from " + url, e);
        }
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

    static String hostOf(String url) {
        String host = URI.create(url).getHost();
        return host != null ? host : url;
    }

    /** Some sources send the non-standard {@code image/jpg}; store the standard {@code image/jpeg}. */
    private static String normalize(MediaType contentType) {
        String value = contentType.getType() + "/" + contentType.getSubtype();
        return "image/jpg".equalsIgnoreCase(value) ? MediaType.IMAGE_JPEG_VALUE : value;
    }
}
