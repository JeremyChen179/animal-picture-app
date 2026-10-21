package com.example.animalpictures.adapter.in.web;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.domain.PictureId;
import com.example.animalpictures.application.FetchPicturesUseCase;
import com.example.animalpictures.application.PictureQueryUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pictures")
public class PictureController {

    private final FetchPicturesUseCase fetchPictures;
    private final PictureQueryUseCase pictureQueries;

    public PictureController(FetchPicturesUseCase fetchPictures, PictureQueryUseCase pictureQueries) {
        this.fetchPictures = fetchPictures;
        this.pictureQueries = pictureQueries;
    }

    /** a. Fetches {@code count} random pictures of the given animal and stores them. */
    @PostMapping
    public ResponseEntity<List<PictureResponse>> fetchAndSave(@Valid @RequestBody FetchPicturesRequest request) {
        AnimalType animalType = AnimalType.fromString(request.animal());
        List<PictureResponse> saved = fetchPictures.fetchAndSave(animalType, request.count()).stream()
                .map(PictureResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Recently stored pictures (metadata only), newest first; optionally for one animal. */
    @GetMapping
    public List<PictureResponse> recent(
            @RequestParam(required = false) String animal,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 50, message = "limit must be at most 50") int limit) {
        AnimalType animalType = animal == null || animal.isBlank() ? null : AnimalType.fromString(animal);
        return pictureQueries.findRecent(animalType, limit).stream().map(PictureResponse::from).toList();
    }

    /** b. The latest stored picture of any animal, as an image. */
    @GetMapping("/latest")
    public ResponseEntity<byte[]> latestImage() {
        return image(pictureQueries.findLatest()
                .orElseThrow(() -> new PictureNotFoundException("No picture stored yet")));
    }

    @GetMapping("/latest/metadata")
    public PictureResponse latestMetadata() {
        return pictureQueries.findLatest()
                .map(PictureResponse::from)
                .orElseThrow(() -> new PictureNotFoundException("No picture stored yet"));
    }

    /** The latest stored picture of the given animal, as an image. */
    @GetMapping("/{animal}/last")
    public ResponseEntity<byte[]> lastImage(@PathVariable String animal) {
        return image(pictureQueries.findLatest(AnimalType.fromString(animal))
                .orElseThrow(() -> new PictureNotFoundException("No " + animal + " picture stored yet")));
    }

    @GetMapping("/{animal}/last/metadata")
    public PictureResponse lastMetadata(@PathVariable String animal) {
        return pictureQueries.findLatest(AnimalType.fromString(animal))
                .map(PictureResponse::from)
                .orElseThrow(() -> new PictureNotFoundException("No " + animal + " picture stored yet"));
    }

    /** Any stored picture by its id (a UUID). An id that isn't a valid UUID is a 400. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> imageById(@PathVariable String id) {
        PictureId pictureId = PictureId.of(id);
        return image(pictureQueries.findById(pictureId)
                .orElseThrow(() -> new PictureNotFoundException("Picture " + pictureId + " not found")));
    }

    private static ResponseEntity<byte[]> image(Picture picture) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.image().contentType()))
                .header("X-Picture-Id", picture.id().toString())
                .body(picture.image().bytes());
    }
}
