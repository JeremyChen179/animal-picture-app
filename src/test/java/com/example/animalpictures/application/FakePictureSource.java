package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.FetchedImage;
import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.PictureSourceException;
import com.example.animalpictures.application.port.out.SourceStatus;
import com.example.animalpictures.domain.AnimalType;
import java.util.List;

/** Test double for the PictureSource port: returns the given images in turn (repeating the last one). */
class FakePictureSource implements PictureSource {

    private final FetchedImage[] images;
    int calls;
    /** Throw on every call after this many successful ones (-1 = never fail). */
    int failAfter = -1;

    FakePictureSource(FetchedImage... images) {
        this.images = images;
    }

    @Override
    public FetchedImage fetchRandom(AnimalType animalType) {
        calls++;
        if (failAfter >= 0 && calls > failAfter) {
            throw new PictureSourceException("source down");
        }
        return images[Math.min(calls - 1, images.length - 1)];
    }

    @Override
    public List<SourceStatus> statuses() {
        return List.of();
    }
}
