package com.example.animalpictures.api;

import com.example.animalpictures.domain.AnimalType;
import com.example.animalpictures.domain.Picture;
import com.example.animalpictures.service.PictureService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pictures")
public class PictureController {

    private final PictureService service;

    public PictureController(PictureService service) {
        this.service = service;
    }

    /** a. Fetches {@code count} random pictures of the given animal and stores them. */
    @PostMapping
    public ResponseEntity<List<PictureResponse>> fetchAndSave(@Valid @RequestBody FetchPicturesRequest request) {
        AnimalType animalType = AnimalType.fromString(request.animal());
        List<PictureResponse> saved = service.fetchAndSave(animalType, request.count()).stream()
                .map(PictureResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** b. The latest stored picture of any animal, as an image. */
    @GetMapping("/latest")
    public ResponseEntity<byte[]> latestImage() {
        return image(service.findLatest()
                .orElseThrow(() -> new PictureNotFoundException("No picture stored yet")));
    }

    @GetMapping("/latest/metadata")
    public PictureResponse latestMetadata() {
        return service.findLatest()
                .map(PictureResponse::from)
                .orElseThrow(() -> new PictureNotFoundException("No picture stored yet"));
    }

    /** The latest stored picture of the given animal, as an image. */
    @GetMapping("/{animal}/last")
    public ResponseEntity<byte[]> lastImage(@PathVariable String animal) {
        return image(service.findLatest(AnimalType.fromString(animal))
                .orElseThrow(() -> new PictureNotFoundException("No " + animal + " picture stored yet")));
    }

    @GetMapping("/{animal}/last/metadata")
    public PictureResponse lastMetadata(@PathVariable String animal) {
        return service.findLatest(AnimalType.fromString(animal))
                .map(PictureResponse::from)
                .orElseThrow(() -> new PictureNotFoundException("No " + animal + " picture stored yet"));
    }

    @GetMapping("/{id:\\d+}/image")
    public ResponseEntity<byte[]> imageById(@PathVariable long id) {
        return image(service.findById(id)
                .orElseThrow(() -> new PictureNotFoundException("Picture " + id + " not found")));
    }

    private static ResponseEntity<byte[]> image(Picture picture) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .header("X-Picture-Id", String.valueOf(picture.id()))
                .body(picture.image());
    }
}
