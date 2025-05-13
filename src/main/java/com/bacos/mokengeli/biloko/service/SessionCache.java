package com.bacos.mokengeli.biloko.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public  class SessionCache {
    private final Cache<String, List<String>> cache;

    public SessionCache(@Value("${session.cache.ttl-seconds:120}") long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(500_000)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    public List<String> get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, List<String> value) {
        cache.put(key, value);            // TTL appliqué globalement par expireAfterWrite()
    }
}