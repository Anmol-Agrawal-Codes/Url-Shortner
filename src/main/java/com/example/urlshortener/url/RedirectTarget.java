package com.example.urlshortener.url;

import java.time.Instant;

public record RedirectTarget(Long id, String url, Instant expiresAt) {
}
