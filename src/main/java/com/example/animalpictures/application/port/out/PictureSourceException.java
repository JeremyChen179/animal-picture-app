package com.example.animalpictures.application.port.out;

/** Thrown when an upstream picture API fails or returns something that is not an image. */
public class PictureSourceException extends RuntimeException {

    public PictureSourceException(String message) {
        super(message);
    }

    public PictureSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
