package com.example.urlshortener.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Credentials(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,30}") String username,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
