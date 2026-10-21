package com.example.animalpictures.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void pictureIdRoundTripsThroughItsCanonicalString() {
        PictureId id = PictureId.newId();

        assertThat(PictureId.of(id.toString())).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "42", "not-a-uuid", "1-1-1-1-1", "3f2a8c1e5b7d4e9a8c3f1a2b3c4d5e6f"})
    void pictureIdRejectsNonCanonicalValues(String value) {
        assertThatThrownBy(() -> PictureId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid picture id '" + value + "'");
    }

    @Test
    void imageIsAValueWithInvariants() {
        assertThat(new Image(new byte[] {1, 2}, "image/png")).isEqualTo(new Image(new byte[] {1, 2}, "image/png"));
        assertThat(new Image(new byte[] {1, 2}, "image/png")).isNotEqualTo(new Image(new byte[] {1, 3}, "image/png"));
        assertThatThrownBy(() -> new Image(new byte[0], "image/png")).hasMessage("image must not be empty");
        assertThatThrownBy(() -> new Image(new byte[] {1}, "text/html")).hasMessage("not an image content type: text/html");
    }

    @Test
    void imageCannotBeChangedFromOutside() {
        byte[] input = {1, 2, 3};
        Image image = new Image(input, "image/jpeg");

        input[0] = 9;                 // changing the caller's array...
        image.bytes()[1] = 9;         // ...or the returned copy...
        assertThat(image.bytes()).containsExactly(1, 2, 3); // ...doesn't change the image
    }

    @Test
    void imageOriginRequiresWebsiteAndUrl() {
        assertThatThrownBy(() -> new ImageOrigin(" ", "https://x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageOrigin("x.com", null)).isInstanceOf(NullPointerException.class);
    }
}
