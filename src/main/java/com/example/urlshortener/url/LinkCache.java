package com.example.urlshortener.url;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LinkCache {

    @CacheEvict(cacheNames = "redirects", key = "#code")
    public void evict(String code) {
    }

    @TransactionalEventListener
    @CacheEvict(cacheNames = "redirects", key = "#event.code()")
    public void onChange(LinkChanged event) {
    }
}
