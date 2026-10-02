package com.example.urlshortener.url;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpiredLinkCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredLinkCleanup.class);
    private final UrlMappingRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration retention;

    public ExpiredLinkCleanup(UrlMappingRepository repository, ApplicationEventPublisher events,
            Clock clock, @Value("${app.expired-retention}") Duration retention) {
        if (retention.isNegative()) {
            throw new IllegalArgumentException("Expired retention must not be negative");
        }
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.cleanup-delay-ms}", initialDelayString = "${app.cleanup-delay-ms}")
    @Transactional
    public void clean() {
        Instant cutoff = clock.instant().minus(retention);
        var ids = repository.findExpiredIds(cutoff, PageRequest.of(0, 100));
        for (Long id : ids) {
            repository.findLockedById(id).ifPresent(mapping -> {
                if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(cutoff)) {
                    repository.delete(mapping);
                    events.publishEvent(new LinkChanged(mapping.getCode()));
                    log.info("Removing expired link {}", mapping.getCode());
                }
            });
        }
    }
}
