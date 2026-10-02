package com.example.urlshortener.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import com.example.urlshortener.url.UrlMappingRepository;
import com.example.urlshortener.url.UrlMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickService {

    public record StatsResponse(long totalClicks, Map<String, Long> clicksPerDay) {
    }

    private static final Logger log = LoggerFactory.getLogger(ClickService.class);
    private final ClickRepository clicks;
    private final UrlMappingRepository mappings;
    private final UrlMappingService links;

    public ClickService(ClickRepository clicks, UrlMappingRepository mappings, UrlMappingService links) {
        this.clicks = clicks;
        this.mappings = mappings;
        this.links = links;
    }

    @Async
    @Transactional
    public void record(Long mappingId, Instant timestamp, String referrer) {
        var mapping = mappings.findLockedById(mappingId);
        if (mapping.isEmpty()) {
            log.warn("Click not recorded: link {} was deleted before async recording", mappingId);
            return;
        }
        String limitedReferrer = referrer == null ? null : referrer.substring(0, Math.min(2048, referrer.length()));
        clicks.save(new Click(mapping.get(), timestamp, limitedReferrer));
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String code, Long ownerId) {
        Long mappingId = links.get(code, ownerId).getId();
        Map<String, Long> daily = new TreeMap<>();
        for (ClickRepository.DailyCount count : clicks.countPerDay(mappingId)) {
            daily.put(count.getClickDate(), count.getTotal());
        }
        return new StatsResponse(daily.values().stream().mapToLong(Long::longValue).sum(), daily);
    }
}
