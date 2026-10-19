package com.example.animalpictures.client;

/** Raw image as downloaded from an upstream picture API. */
public record FetchedImage(byte[] data, String contentType, String sourceUrl) {
}
