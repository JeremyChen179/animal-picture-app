package com.example.animalpictures.client;

import com.example.animalpictures.domain.AnimalType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AnimalPictureClient {

    private static final Logger log = LoggerFactory.getLogger(AnimalPictureClient.class);

    private final RestClient restClient;
    private final PictureSourceProperties properties;

    public AnimalPictureClient(RestClient pictureRestClient, PictureSourceProperties properties) {
        this.restClient = pictureRestClient;
        this.properties = properties;
    }

    /** Tries each configured source in order and returns the first image; fails only if all sources fail. */
    public FetchedImage fetchRandom(AnimalType animalType) {
        List<String> templates = properties.sources().getOrDefault(animalType, List.of());
        if (templates.isEmpty()) {
            throw new PictureSourceException("No picture source configured for " + animalType);
        }
        PictureSourceException lastFailure = null;
        for (String template : templates) {
            try {
                return fetchFrom(animalType, template);
            } catch (PictureSourceException e) {
                log.warn("{} source failed, trying next if available: {}", animalType, e.getMessage());
                lastFailure = e;
            }
        }
        throw new PictureSourceException(
                "All " + templates.size() + " " + animalType + " picture source(s) failed. Last error: "
                        + lastFailure.getMessage(), lastFailure);
    }

    private FetchedImage fetchFrom(AnimalType animalType, String template) {
        String url = UriComponentsBuilder.fromUriString(template)
                .buildAndExpand(Map.of("width", randomSize(), "height", randomSize()))
                .toUriString();
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
            return new FetchedImage(body, contentType.toString(), url);
        } catch (RestClientException e) {
            throw new PictureSourceException("Failed to fetch " + animalType + " picture from " + url, e);
        }
    }

    private int randomSize() {
        return ThreadLocalRandom.current().nextInt(properties.minSize(), properties.maxSize() + 1);
    }
}
