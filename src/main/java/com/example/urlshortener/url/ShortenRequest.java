package com.example.urlshortener.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortenRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(min = 3, max = 40)
        @Pattern(regexp = "(?=.*-)[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]",
                message = "Alias must contain a hyphen and start and end with an alphanumeric character")
        String alias,
        Instant expiresAt) {
}
