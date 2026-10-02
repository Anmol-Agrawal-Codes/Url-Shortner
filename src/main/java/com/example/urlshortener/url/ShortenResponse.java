package com.example.urlshortener.url;

import java.time.Instant;

public record ShortenResponse(String code, String originalUrl, String shortUrl, Instant expiresAt) {
}
