package com.example.urlshortener.url;

import java.time.Clock;
import java.time.Instant;
import com.example.urlshortener.auth.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UrlMappingService {

    private final UrlMappingRepository repository;
    private final UserRepository users;
    private final UrlValidator validator;
    private final RedirectLookup lookup;
    private final LinkCache cache;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UrlMappingService(UrlMappingRepository repository, UserRepository users, UrlValidator validator,
            RedirectLookup lookup, LinkCache cache, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.validator = validator;
        this.lookup = lookup;
        this.cache = cache;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UrlMapping shorten(ShortenRequest request, Long ownerId) {
        validator.validate(request.url());
        validateExpiry(request.expiresAt());
        if (request.alias() != null && repository.existsByCode(request.alias())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alias already exists");
        }
        var owner = users.findById(ownerId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        UrlMapping mapping = repository.save(new UrlMapping(request.url(), request.expiresAt(), owner));
        if (request.alias() == null) {
            // Skip IDs whose encoded codes were used by the earlier decimal-code milestone.
            while (repository.existsByCode(Base62.encode(mapping.getId()))) {
                repository.delete(mapping);
                repository.flush();
                mapping = repository.save(new UrlMapping(request.url(), request.expiresAt(), owner));
            }
        }
        mapping.setCode(request.alias() == null ? Base62.encode(mapping.getId()) : request.alias());
        repository.flush();
        events.publishEvent(new LinkChanged(mapping.getCode()));
        return mapping;
    }

    @Transactional(readOnly = true)
    public UrlMapping get(String code, Long ownerId) {
        return repository.findByCodeAndOwnerId(code, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Short link not found"));
    }

    @Transactional(readOnly = true)
    public Page<UrlMapping> list(Long ownerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be >= 0; size must be 1 to 100");
        }
        return repository.findByOwnerId(ownerId, PageRequest.of(page, size, Sort.by("id").descending()));
    }

    @Transactional
    public UrlMapping update(String code, Long ownerId, UpdateLinkRequest request) {
        UrlMapping mapping = ownedForUpdate(code, ownerId);
        validator.validate(request.url());
        validateExpiry(request.expiresAt());
        mapping.update(request.url(), request.expiresAt());
        events.publishEvent(new LinkChanged(code));
        return mapping;
    }

    @Transactional
    public void delete(String code, Long ownerId) {
        repository.delete(ownedForUpdate(code, ownerId));
        events.publishEvent(new LinkChanged(code));
    }

    public RedirectTarget resolve(String code) {
        RedirectTarget target = lookup.find(code);
        if (target.expiresAt() != null && !target.expiresAt().isAfter(clock.instant())) {
            cache.evict(code);
            throw new ResponseStatusException(HttpStatus.GONE, "Short link has expired");
        }
        return target;
    }

    private UrlMapping ownedForUpdate(String code, Long ownerId) {
        return repository.findOwnedForUpdate(code, ownerId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short link not found"));
    }

    private void validateExpiry(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
        }
    }
}
