package com.example.animalpictures.domain;

import java.util.Arrays;
import java.util.Locale;

/** Supported animals. Parsing is case-insensitive so the API accepts "cat", "Cat" or "CAT". */
public enum AnimalType {
    CAT,
    DOG,
    BEAR;

    public static AnimalType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Animal type is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown animal type '" + value + "'. Supported: " + Arrays.toString(values()));
        }
    }
}
