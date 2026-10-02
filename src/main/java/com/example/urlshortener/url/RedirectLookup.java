package com.example.urlshortener.url;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RedirectLookup {

    private final UrlMappingRepository repository;

    public RedirectLookup(UrlMappingRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "redirects", key = "#code", sync = true)
    @Transactional(readOnly = true)
    public RedirectTarget find(String code) {
        UrlMapping mapping = repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
        return new RedirectTarget(mapping.getId(), mapping.getOriginalUrl(), mapping.getExpiresAt());
    }
}
