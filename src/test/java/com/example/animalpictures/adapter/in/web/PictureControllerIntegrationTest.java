package com.example.animalpictures.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSourceException;
import com.example.animalpictures.application.port.out.SourceStatus;
import java.util.List;
import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.adapter.out.persistence.SqlitePictureStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application with a real SQLite file (created from schema.sql in a not-yet-existing directory);
 * only the upstream picture APIs are mocked.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/it/pictures.db",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class PictureControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SqlitePictureStore repository;

    @MockitoBean
    private PictureSource client;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsSqliteFileOnTheFly() {
        assertThat(Files.exists(Path.of("target/test-data/it/pictures.db"))).isTrue();
    }

    @Test
    void fetchSaveAndReturnLatestPicture() throws Exception {
        when(client.fetchRandom(AnimalType.BEAR))
                .thenReturn(new FetchedImage(new byte[] {1}, "image/jpeg", "https://placebear.com/300/200", "placebear.com"))
                .thenReturn(new FetchedImage(new byte[] {2, 2}, "image/png", "https://placebear.com/400/300", "placebear.com"));

        mockMvc.perform(post("/api/pictures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"bear\", \"count\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].animalType").value("BEAR"))
                .andExpect(jsonPath("$[0].sourceWebsite").value("placebear.com"))
                .andExpect(jsonPath("$[1].sourceUrl").value("https://placebear.com/400/300"));

        mockMvc.perform(get("/api/pictures/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {2, 2}))
                .andExpect(header().exists("X-Picture-Id"));

        mockMvc.perform(get("/api/pictures/bear/last/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").value("https://placebear.com/400/300"))
                .andExpect(jsonPath("$.sizeBytes").value(2))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void latestOfAnyAnimalVersusPerAnimal() throws Exception {
        when(client.fetchRandom(AnimalType.CAT))
                .thenReturn(new FetchedImage(new byte[] {7}, "image/jpeg", "https://cataas.com/cat", "cataas.com"));
        when(client.fetchRandom(AnimalType.DOG))
                .thenReturn(new FetchedImage(new byte[] {8}, "image/jpeg", "https://place.dog/300/200", "place.dog"));
        saveOne("cat");
        saveOne("dog");

        mockMvc.perform(get("/api/pictures/latest/metadata"))
                .andExpect(jsonPath("$.animalType").value("DOG"));
        mockMvc.perform(get("/api/pictures/cat/last"))
                .andExpect(content().bytes(new byte[] {7}));
        mockMvc.perform(get("/api/pictures/bear/last"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No bear picture stored yet"));
    }

    @Test
    void latestReturns404WhenEmpty() throws Exception {
        mockMvc.perform(get("/api/pictures/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No picture stored yet"));
    }

    @Test
    void rejectsUnknownAnimal() throws Exception {
        mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"unicorn\", \"count\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown animal type 'unicorn'. Supported: [CAT, DOG, BEAR]"));
    }

    @Test
    void rejectsInvalidCount() throws Exception {
        mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"cat\", \"count\": 11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("count must be at most 10"));
    }

    /** count must be a JSON integer in 1..10: no silent rounding of 1.5, no "3" strings, no int overflow. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "\"3\"", "2147483648", "-2147483649", "99999999999999999999", "true"})
    void rejectsCountThatIsNotAJsonIntegerInRange(String count) throws Exception {
        mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"cat\", \"count\": " + count + "}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadGatewayAndSavesNothingWhenUpstreamFails() throws Exception {
        when(client.fetchRandom(any())).thenThrow(new PictureSourceException("place.dog is down"));

        mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"dog\", \"count\": 1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("place.dog is down"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void corsPreflightAllowedForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/pictures")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsRejectedForOtherOrigin() throws Exception {
        mockMvc.perform(options("/api/pictures")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsRecentPicturesNewestFirstWithFilterAndLimit() throws Exception {
        when(client.fetchRandom(AnimalType.CAT))
                .thenReturn(new FetchedImage(new byte[] {1}, "image/jpeg", "https://cataas.com/cat", "cataas.com"));
        when(client.fetchRandom(AnimalType.DOG))
                .thenReturn(new FetchedImage(new byte[] {2, 2}, "image/jpeg", "https://place.dog/300/200", "place.dog"));
        saveOne("cat");
        saveOne("dog");
        saveOne("cat");

        mockMvc.perform(get("/api/pictures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].animalType").value("CAT"))
                .andExpect(jsonPath("$[1].animalType").value("DOG"))
                .andExpect(jsonPath("$[1].sizeBytes").value(2))
                .andExpect(jsonPath("$[1].imageUrl").exists());

        mockMvc.perform(get("/api/pictures").param("animal", "dog"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceWebsite").value("place.dog"));

        mockMvc.perform(get("/api/pictures").param("limit", "1"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listIsEmptyWhenNothingStored() throws Exception {
        mockMvc.perform(get("/api/pictures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listRejectsInvalidLimitAndAnimal() throws Exception {
        mockMvc.perform(get("/api/pictures").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be at most 50"));
        mockMvc.perform(get("/api/pictures").param("animal", "unicorn"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesUiWithGalleryAndSourceStatus() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"gallery\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"sources\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/sources")));
    }

    @Test
    void saveAllIsAtomicWhenOneInsertFails() {
        java.time.Instant now = java.time.Instant.parse("2026-10-20T10:00:00Z");
        com.example.animalpictures.domain.Picture valid = new com.example.animalpictures.domain.Picture(
                null, AnimalType.CAT, new byte[] {1}, "image/jpeg", "cataas.com", "https://cataas.com/cat", now);
        // content_type is NOT NULL in schema.sql, so this insert fails after the first one succeeded.
        com.example.animalpictures.domain.Picture invalid = new com.example.animalpictures.domain.Picture(
                null, AnimalType.CAT, new byte[] {2}, null, "cataas.com", "https://cataas.com/cat", now);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.saveAll(List.of(valid, invalid)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.count()).isZero(); // the first insert was rolled back
    }

    @Test
    void healthEndpointIsUpAndOtherActuatorEndpointsAreHidden() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test
    void sourcesEndpointListsCircuitBreakerStates() throws Exception {
        when(client.statuses()).thenReturn(List.of(
                new SourceStatus(AnimalType.CAT, 0, "cataas.com", "https://cataas.com/cat", "OPEN", 2, 2),
                new SourceStatus(AnimalType.CAT, 1, "placecats.com", "https://placecats.com/{width}/{height}", "CLOSED", 2, 0)));

        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].website").value("cataas.com"))
                .andExpect(jsonPath("$[0].state").value("OPEN"))
                .andExpect(jsonPath("$[1].state").value("CLOSED"));
    }

    private void saveOne(String animal) throws Exception {
        mockMvc.perform(post("/api/pictures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"" + animal + "\", \"count\": 1}"))
                .andExpect(status().isCreated());
    }
}
