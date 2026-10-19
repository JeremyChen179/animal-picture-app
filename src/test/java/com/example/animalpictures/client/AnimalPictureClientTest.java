package com.example.animalpictures.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.animalpictures.client.PictureSourceProperties.Source;
import com.example.animalpictures.domain.AnimalType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnimalPictureClientTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private MockRestServiceServer server;

    /** Fixed size (min == max) makes the expanded URLs predictable. */
    private AnimalPictureClient clientWith(Map<AnimalType, List<Source>> sources) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AnimalPictureClient(builder.build(), new PictureSourceProperties(sources, 300, 300));
    }

    // --- image sources ---

    @Test
    void fetchesImageFromImageSource() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.BEAR, List.of(Source.image("https://placebear.test/{width}/{height}"))));
        server.expect(requestTo("https://placebear.test/300/300"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        FetchedImage image = client.fetchRandom(AnimalType.BEAR);

        assertThat(image.data()).isEqualTo(JPEG);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.sourceUrl()).isEqualTo("https://placebear.test/300/300");
        assertThat(image.sourceWebsite()).isEqualTo("placebear.test");
        server.verify();
    }

    @Test
    void normalizesNonStandardImageJpgContentType() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.DOG, List.of(Source.image("https://placedog.test/{width}/{height}"))));
        server.expect(requestTo("https://placedog.test/300/300"))
                .andRespond(withSuccess(JPEG, MediaType.parseMediaType("image/jpg")));

        assertThat(client.fetchRandom(AnimalType.DOG).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsNonImageResponse() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.BEAR, List.of(Source.image("https://placebear.test/{width}/{height}"))));
        server.expect(requestTo("https://placebear.test/300/300"))
                .andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("did not return an image");
    }

    @Test
    void wrapsUpstreamErrors() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.BEAR, List.of(Source.image("https://placebear.test/{width}/{height}"))));
        server.expect(requestTo("https://placebear.test/300/300")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("All 1 BEAR picture source(s) failed")
                .hasMessageContaining("Failed to fetch BEAR picture");
    }

    // --- JSON sources ---

    @Test
    void jsonSourceResolvesImageUrlThenDownloadsImage() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.DOG, List.of(
                new Source("https://dogs.test/api/random", "/message", null, null, null))));
        server.expect(requestTo("https://dogs.test/api/random"))
                .andRespond(withSuccess("{\"message\": \"https://images.dogs.test/collie.jpg\", \"status\": \"success\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://images.dogs.test/collie.jpg"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        FetchedImage image = client.fetchRandom(AnimalType.DOG);

        assertThat(image.sourceUrl()).isEqualTo("https://images.dogs.test/collie.jpg");
        // The website is the source API's host, not the image host.
        assertThat(image.sourceWebsite()).isEqualTo("dogs.test");
        assertThat(image.data()).isEqualTo(JPEG);
        server.verify();
    }

    @Test
    void jsonSourceUsesNestedPointerRandomPlaceholderAndUrlRewrite() {
        // randomMax = 1 makes {random} always 1, so the URL is predictable.
        AnimalPictureClient client = clientWith(Map.of(AnimalType.BEAR, List.of(new Source(
                "https://bears.test/observations?per_page=1&page={random}",
                "/results/0/photos/0/url", "/square.", "/medium.", 1))));
        server.expect(requestTo("https://bears.test/observations?per_page=1&page=1"))
                .andRespond(withSuccess("{\"results\": [{\"photos\": [{\"url\": \"https://photos.test/1/square.jpg\"}]}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://photos.test/1/medium.jpg"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        assertThat(client.fetchRandom(AnimalType.BEAR).sourceUrl()).isEqualTo("https://photos.test/1/medium.jpg");
        server.verify();
    }

    @Test
    void jsonSourceWithoutImageUrlFails() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.BEAR, List.of(new Source(
                "https://bears.test/observations", "/results/0/photos/0/url", null, null, null))));
        server.expect(requestTo("https://bears.test/observations"))
                .andRespond(withSuccess("{\"total_results\": 0, \"results\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("returned no image URL at /results/0/photos/0/url");
    }

    // --- fallback ---

    @Test
    void fallsBackToNextSourceWhenFirstFails() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.CAT, List.of(
                Source.image("https://cats-primary.test/{width}/{height}"),
                Source.image("https://cats-backup.test/{width}/{height}"))));
        server.expect(requestTo("https://cats-primary.test/300/300")).andRespond(withServerError());
        server.expect(requestTo("https://cats-backup.test/300/300")).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        assertThat(client.fetchRandom(AnimalType.CAT).sourceUrl()).isEqualTo("https://cats-backup.test/300/300");
        server.verify();
    }

    @Test
    void fallsBackFromImageSourceToJsonSource() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.DOG, List.of(
                Source.image("https://dogs-primary.test/{width}/{height}"),
                new Source("https://dogs-api.test/random", "/message", null, null, null))));
        server.expect(requestTo("https://dogs-primary.test/300/300")).andRespond(withServerError());
        server.expect(requestTo("https://dogs-api.test/random"))
                .andRespond(withSuccess("{\"message\": \"https://img.test/d.jpg\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://img.test/d.jpg")).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        assertThat(client.fetchRandom(AnimalType.DOG).sourceUrl()).isEqualTo("https://img.test/d.jpg");
        server.verify();
    }

    @Test
    void failsWhenAllSourcesFail() {
        AnimalPictureClient client = clientWith(Map.of(AnimalType.CAT, List.of(
                Source.image("https://cats-primary.test/{width}/{height}"),
                Source.image("https://cats-backup.test/{width}/{height}"))));
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
        AnimalPictureClient client = clientWith(Map.of());

        assertThatThrownBy(() -> client.fetchRandom(AnimalType.DOG))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("No picture source configured for DOG");
    }
}
