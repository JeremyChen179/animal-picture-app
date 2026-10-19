package com.example.animalpictures.client;

/**
 * Raw image as downloaded from an upstream picture API.
 *
 * @param sourceUrl     URL the image bytes were downloaded from
 * @param sourceWebsite host of the configured source API, e.g. {@code placebear.com} or {@code api.inaturalist.org}
 *                      (for JSON sources this differs from the image host)
 */
public record FetchedImage(byte[] data, String contentType, String sourceUrl, String sourceWebsite) {
}
