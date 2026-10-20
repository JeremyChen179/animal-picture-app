package com.example.animalpictures.application;

import com.example.animalpictures.application.port.out.PictureSource;
import com.example.animalpictures.application.port.out.SourceStatus;
import java.util.List;

/** Reports which picture sources are in use and which are temporarily skipped. */
public class SourceStatusUseCase {

    private final PictureSource pictureSource;

    public SourceStatusUseCase(PictureSource pictureSource) {
        this.pictureSource = pictureSource;
    }

    public List<SourceStatus> statuses() {
        return pictureSource.statuses();
    }
}
