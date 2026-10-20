package com.example.animalpictures.adapter.out.pictures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonPointerTest {

    private final Map<String, Object> json = Map.of(
            "message", "https://img/dog.jpg",
            "results", List.of(Map.of("photos", List.of(Map.of("url", "https://img/bear.jpg")))),
            "a/b", "escaped");

    @Test
    void findsTopLevelAndNestedValues() {
        assertThat(JsonPointer.find(json, "/message")).contains("https://img/dog.jpg");
        assertThat(JsonPointer.find(json, "/results/0/photos/0/url")).contains("https://img/bear.jpg");
        assertThat(JsonPointer.find(json, "/a~1b")).contains("escaped");
    }

    @Test
    void returnsEmptyForMissingPaths() {
        assertThat(JsonPointer.find(json, "/missing")).isEmpty();
        assertThat(JsonPointer.find(json, "/results/5/photos")).isEmpty();
        assertThat(JsonPointer.find(json, "/message/0")).isEmpty();
        assertThat(JsonPointer.find(null, "/message")).isEmpty();
    }

    @Test
    void rejectsPointerWithoutLeadingSlash() {
        assertThatThrownBy(() -> JsonPointer.find(json, "message")).isInstanceOf(IllegalArgumentException.class);
    }
}
