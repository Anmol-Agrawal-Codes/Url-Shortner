package com.example.urlshortener.url;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import com.example.urlshortener.analytics.ClickService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UrlMappingController {

    public record LinkPage(List<ShortenResponse> content, int page, int size, long totalElements, int totalPages) {
    }

    private static final Logger log = LoggerFactory.getLogger(UrlMappingController.class);
    private final UrlMappingService service;
    private final String baseUrl;
    private final CreateRateLimiter rateLimiter;
    private final ClickService clicks;
    private final Clock clock;

    public UrlMappingController(UrlMappingService service, @Value("${app.base-url}") String baseUrl,
            CreateRateLimiter rateLimiter, ClickService clicks, Clock clock) {
        this.service = service;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        URI publicAddress = URI.create(this.baseUrl);
        if (!("http".equalsIgnoreCase(publicAddress.getScheme())
                || "https".equalsIgnoreCase(publicAddress.getScheme())) || publicAddress.getHost() == null
                || publicAddress.getUserInfo() != null || publicAddress.getQuery() != null
                || publicAddress.getFragment() != null) {
            throw new IllegalArgumentException("APP_BASE_URL must be an absolute HTTP(S) address");
        }
        this.rateLimiter = rateLimiter;
        this.clicks = clicks;
        this.clock = clock;
    }

    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long ownerId = Long.valueOf(jwt.getSubject());
        rateLimiter.consume(ownerId);
        ShortenResponse response = response(service.shorten(request, ownerId));
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
            @RequestHeader(value = "Referer", required = false) String referrer) {
        RedirectTarget target = service.resolve(code);
        try {
            clicks.record(target.id(), clock.instant(), referrer);
        } catch (TaskRejectedException exception) {
            log.error("Click recording queue is full for link {}", code, exception);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target.url())).build();
    }

    @GetMapping("/api/links/{code}")
    public ShortenResponse get(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return response(service.get(code, Long.valueOf(jwt.getSubject())));
    }

    @GetMapping("/api/links")
    public LinkPage list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var links = service.list(Long.valueOf(jwt.getSubject()), page, size);
        return new LinkPage(links.getContent().stream().map(this::response).toList(), links.getNumber(),
                links.getSize(), links.getTotalElements(), links.getTotalPages());
    }

    @PutMapping("/api/links/{code}")
    public ShortenResponse update(@PathVariable String code, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateLinkRequest request) {
        return response(service.update(code, Long.valueOf(jwt.getSubject()), request));
    }

    @DeleteMapping("/api/links/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        service.delete(code, Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/links/{code}/stats")
    public ClickService.StatsResponse stats(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return clicks.stats(code, Long.valueOf(jwt.getSubject()));
    }

    private ShortenResponse response(UrlMapping mapping) {
        return new ShortenResponse(mapping.getCode(), mapping.getOriginalUrl(),
                baseUrl + "/" + mapping.getCode(), mapping.getExpiresAt());
    }
}
