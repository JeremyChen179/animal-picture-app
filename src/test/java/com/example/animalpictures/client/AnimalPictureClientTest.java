package com.example.animalpictures.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.animalpictures.domain.AnimalType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnimalPictureClientTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private MockRestServiceServer server;
    private AnimalPictureClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Fixed size (min == max) makes the expanded URL predictable.
        PictureSourceProperties properties = new PictureSourceProperties(
                Map.of(AnimalType.BEAR, List.of("https://placebear.test/{width}/{height}"),
                        AnimalType.CAT, List.of("https://cats-primary.test/{width}/{height}",
                                "https://cats-backup.test/{width}/{height}")),
                300, 300);
        client = new AnimalPictureClient(builder.build(), properties);
    }

    @Test
    void fetchesImageFromConfiguredSource() {
        server.expect(requestTo("https://placebear.test/300/300"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        FetchedImage image = client.fetchRandom(AnimalType.BEAR);

        assertThat(image.data()).isEqualTo(JPEG);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.sourceUrl()).isEqualTo("https://placebear.test/300/300");
        server.verify();
    }

    @Test
    void rejectsNonImageResponse() {
        server.expect(requestTo("https://placebear.test/300/300"))
                .andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("did not return an image");
    }

    @Test
    void wrapsUpstreamErrors() {
        server.expect(requestTo("https://placebear.test/300/300")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("All 1 BEAR picture source(s) failed")
                .hasMessageContaining("Failed to fetch BEAR picture");
    }

    @Test
    void fallsBackToNextSourceWhenFirstFails() {
        server.expect(requestTo("https://cats-primary.test/300/300")).andRespond(withServerError());
        server.expect(requestTo("https://cats-backup.test/300/300")).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        FetchedImage image = client.fetchRandom(AnimalType.CAT);

        assertThat(image.sourceUrl()).isEqualTo("https://cats-backup.test/300/300");
        server.verify();
    }

    @Test
    void failsWhenAllSourcesFail() {
        server.expect(requestTo("https://cats-primary.test/300/300")).andRespond(withServerError());
        server.expect(requestTo("https://cats-backup.test/300/300"))
                .andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.CAT))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("All 2 CAT picture source(s) failed")
                .hasMessageContaining("did not return an image");
    }

    @Test
    void failsWhenAnimalHasNoSource() {
        assertThatThrownBy(() -> client.fetchRandom(AnimalType.DOG))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("No picture source configured for DOG");
    }
}
