package com.example.animalpictures.adapter.out.pictures;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PictureSourceProperties.class)
class ClientConfig {

    /**
     * Upstream picture services can be slow or down; fail fast instead of hanging the request.
     * The User-Agent identifies the app, as public APIs such as iNaturalist ask clients to do.
     */
    @Bean
    RestClient pictureRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "animal-picture-app/1.0 (+https://github.com/JeremyChen179/animal-picture-app)")
                .build();
    }
}
