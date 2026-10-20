package com.example.animalpictures.adapter.in.web;

import com.example.animalpictures.application.SourceStatusUseCase;
import com.example.animalpictures.application.port.out.SourceStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows which picture sources are in use and which are temporarily skipped by their circuit breaker. */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceStatusUseCase sourceStatus;

    public SourceController(SourceStatusUseCase sourceStatus) {
        this.sourceStatus = sourceStatus;
    }

    @GetMapping
    public List<SourceStatus> sources() {
        return sourceStatus.statuses();
    }
}
