package com.example.animalpictures;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.animalpictures.client.PictureSourceProperties;
import com.example.animalpictures.client.PictureSourceProperties.Source;
import com.example.animalpictures.domain.AnimalType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Guards the shipped application.yml: every animal has a backup, and JSON sources bind correctly. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test-data/config/pictures.db")
class ApplicationConfigTest {

    @Autowired
    private PictureSourceProperties properties;

    @Test
    void everyAnimalHasAtLeastOneBackupSource() {
        for (AnimalType animal : AnimalType.values()) {
            assertThat(properties.sources().get(animal)).as(animal + " sources").hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void bearJsonSourceIsBoundFromYaml() {
        Source inaturalist = properties.sources().get(AnimalType.BEAR).get(1);
        assertThat(inaturalist.isJson()).isTrue();
        assertThat(inaturalist.imageUrlPath()).isEqualTo("/results/0/photos/0/url");
        assertThat(inaturalist.imageUrlFind()).isEqualTo("/square.");
        assertThat(inaturalist.imageUrlReplace()).isEqualTo("/medium.");
        assertThat(inaturalist.randomMaxOrDefault()).isEqualTo(7000);
    }

    @Test
    void circuitBreakerSettingsAreBoundFromYaml() {
        assertThat(properties.circuitBreaker().failureThreshold()).isEqualTo(2);
        assertThat(properties.circuitBreaker().openDuration()).isEqualTo(Duration.ofSeconds(60));
    }
}
