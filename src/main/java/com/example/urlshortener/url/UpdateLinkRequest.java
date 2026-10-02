package com.example.urlshortener.url;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLinkRequest(@NotBlank @Size(max = 2048) String url, Instant expiresAt) {
}
