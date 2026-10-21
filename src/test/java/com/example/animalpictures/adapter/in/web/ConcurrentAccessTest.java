package com.example.animalpictures.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.domain.Image;
import com.example.animalpictures.domain.ImageOrigin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test: a slow fetch must not hold the (single) database connection while it downloads,
 * otherwise every other request that needs the database waits and then fails with 500.
 * The pool timeout is lowered to 2 s so the old behaviour fails fast.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/concurrency/pictures.db",
        "spring.datasource.hikari.connection-timeout=2000"
})
@AutoConfigureMockMvc
class ConcurrentAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PictureSource client;

    @Test
    void readsAreNotBlockedWhileAFetchIsDownloading() throws Exception {
        CountDownLatch downloadStarted = new CountDownLatch(1);
        CountDownLatch finishDownload = new CountDownLatch(1);
        when(client.fetchRandom(any())).thenAnswer(invocation -> {
            downloadStarted.countDown();
            finishDownload.await(10, TimeUnit.SECONDS); // simulate a slow upstream API
            return new FetchedImage(new Image(new byte[] {1}, "image/jpeg"), new ImageOrigin("placebear.com", "https://placebear.com/300/200"));
        });

        CompletableFuture<Integer> slowFetch = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/pictures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animal\": \"bear\", \"count\": 1}")).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(downloadStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // While the fetch is still downloading, reads must answer straight away (404: nothing saved yet).
        long start = System.nanoTime();
        int latestStatus = mockMvc.perform(get("/api/pictures/latest")).andReturn().getResponse().getStatus();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        finishDownload.countDown();
        assertThat(latestStatus).isEqualTo(404);
        assertThat(elapsedMillis).isLessThan(1000);
        assertThat(slowFetch.get(10, TimeUnit.SECONDS)).isEqualTo(201);
    }
}
