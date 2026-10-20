package com.example.animalpictures.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FetchPicturesRequest(
        @NotBlank(message = "animal is required") String animal,
        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 10, message = "count must be at most 10") int count) {
}
