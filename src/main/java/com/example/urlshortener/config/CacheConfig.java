package com.example.urlshortener.config;

import java.time.Duration;
import com.example.urlshortener.url.RedirectTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class CacheConfig {

    @Bean
    @Profile("!redis")
    public CacheManager noCacheManager() {
        return new NoOpCacheManager();
    }

    @Bean
    @Profile("redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connections, ObjectMapper mapper,
            @Value("${app.cache-ttl}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        try (var connection = connections.getConnection()) {
            connection.ping();
        }
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, RedirectTarget.class);
        var configuration = RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl)
                .disableCachingNullValues().serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(connections).cacheDefaults(configuration).build();
    }
}
