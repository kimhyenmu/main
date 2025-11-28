package com.example.assignment.storage;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 캐시를 시뮬레이션하는 저장소
 * 실제로는 Redis 등을 사용하지만, 여기서는 ConcurrentHashMap으로 구현
 */
@Component
public class CacheStorage {
    private final ConcurrentHashMap<Long, Long> cache = new ConcurrentHashMap<>();

    public Long get(Long postId) {
        return cache.get(postId);
    }

    public void put(Long postId, Long viewCount) {
        cache.put(postId, viewCount);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
