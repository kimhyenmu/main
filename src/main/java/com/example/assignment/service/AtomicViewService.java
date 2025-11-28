package com.example.assignment.service;

import com.example.assignment.model.Post;
import com.example.assignment.storage.DatabaseStorage;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 원자적 연산(AtomicLong)을 사용한 조회수 증가 서비스
 * 캐시로 AtomicLong을 사용하여 원자적 증가 보장
 * 장점: 락 없이 높은 성능, 증가 연산의 원자성 보장
 * 단점: 캐시-DB 동기화 시점에 대한 추가 고려 필요
 */
@Service
public class AtomicViewService {
    private final DatabaseStorage databaseStorage;
    private final ConcurrentHashMap<Long, AtomicLong> atomicCache = new ConcurrentHashMap<>();

    public AtomicViewService(DatabaseStorage databaseStorage) {
        this.databaseStorage = databaseStorage;
    }

    public Long incrementViewCount(Long postId) {
        AtomicLong viewCount = atomicCache.computeIfAbsent(postId, k -> {
            Post post = databaseStorage.findById(k);
            if (post == null) {
                throw new IllegalArgumentException("Post not found: " + k);
            }
            return new AtomicLong(post.getViewCount());
        });

        // 원자적으로 증가
        long newViewCount = viewCount.incrementAndGet();

        // DB에 비동기적으로 저장 (실제로는 batch나 scheduled로 처리하는 것이 더 효율적)
        Post post = databaseStorage.findById(postId);
        post.setViewCount(newViewCount);
        databaseStorage.save(post);

        return newViewCount;
    }

    public Long getViewCount(Long postId) {
        AtomicLong viewCount = atomicCache.get(postId);
        if (viewCount == null) {
            Post post = databaseStorage.findById(postId);
            return post != null ? post.getViewCount() : 0L;
        }
        return viewCount.get();
    }

    public Long getViewCountFromDb(Long postId) {
        Post post = databaseStorage.findById(postId);
        return post != null ? post.getViewCount() : 0L;
    }

    public void reset() {
        atomicCache.clear();
        databaseStorage.clear();
    }
}
