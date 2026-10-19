package com.example.animalpictures.api;

import com.example.animalpictures.client.AnimalPictureClient;
import com.example.animalpictures.client.SourceStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows which picture sources are in use and which are temporarily skipped by their circuit breaker. */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final AnimalPictureClient client;

    public SourceController(AnimalPictureClient client) {
        this.client = client;
    }

    @GetMapping
    public List<SourceStatus> sources() {
        return client.sourceStatuses();
    }
}
