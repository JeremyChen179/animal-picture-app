package com.example.animalpictures.client;

import com.example.animalpictures.domain.AnimalType;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstream URL templates per animal, tried in order until one returns an image, e.g.
 * {@code https://placebear.com/{width}/{height}}. Width and height are randomised per request
 * so placeholder services return different pictures.
 */
@ConfigurationProperties(prefix = "pictures")
public record PictureSourceProperties(Map<AnimalType, List<String>> sources, int minSize, int maxSize) {
}
