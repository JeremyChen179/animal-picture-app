package com.example.animalpictures.api;

public class PictureNotFoundException extends RuntimeException {

    public PictureNotFoundException(String message) {
        super(message);
    }
}
