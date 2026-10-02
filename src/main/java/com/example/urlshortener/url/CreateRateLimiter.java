package com.example.urlshortener.url;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CreateRateLimiter {

    private record Entry(Bucket bucket, Instant lastUsed) {
    }

    private final ConcurrentHashMap<Long, Entry> buckets = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration period;
    private final Clock clock;

    public CreateRateLimiter(@Value("${app.create-limit}") int limit,
            @Value("${app.create-period}") Duration period, Clock clock) {
        if (limit < 1 || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Rate limit and period must be positive");
        }
        this.limit = limit;
        this.period = period;
        this.clock = clock;
    }

    public void consume(Long userId) {
        Entry entry = buckets.compute(userId, (id, previous) -> new Entry(
                previous == null ? Bucket.builder().addLimit(
                        bandwidth -> bandwidth.capacity(limit).refillGreedy(limit, period)).build()
                        : previous.bucket(), clock.instant()));
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Create rate limit exceeded") {
                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    var headers = new org.springframework.http.HttpHeaders();
                    headers.set("Retry-After", Long.toString(Math.max(1,
                            (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L)));
                    return headers;
                }
            };
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void removeIdleBuckets() {
        Instant cutoff = clock.instant().minus(period.multipliedBy(2));
        buckets.entrySet().removeIf(entry -> entry.getValue().lastUsed().isBefore(cutoff));
    }
}
