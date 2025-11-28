package com.example.assignment.service;

import com.example.assignment.model.Post;
import com.example.assignment.storage.CacheStorage;
import com.example.assignment.storage.DatabaseStorage;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-through 패턴과 게시글별 락을 결합한 조회수 증가 서비스
 * DB 저장과 캐시 갱신의 순서를 일원화하여 불일치 최소화
 * 장점: 캐시와 DB의 일관성 보장, 게시글별 병렬 처리
 * 단점: DB 저장 지연이 응답 시간에 직접 영향
 */
@Service
public class WriteThroughViewService {
    private final CacheStorage cacheStorage;
    private final DatabaseStorage databaseStorage;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public WriteThroughViewService(CacheStorage cacheStorage, DatabaseStorage databaseStorage) {
        this.cacheStorage = cacheStorage;
        this.databaseStorage = databaseStorage;
    }

    public Long incrementViewCount(Long postId) {
        Object lock = locks.computeIfAbsent(postId, k -> new Object());

        synchronized (lock) {
            // 1. DB에서 최신 값 읽기 (single source of truth)
            Post post = databaseStorage.findById(postId);
            if (post == null) {
                throw new IllegalArgumentException("Post not found: " + postId);
            }

            // 2. 값 증가
            Long newViewCount = post.getViewCount() + 1;
            post.setViewCount(newViewCount);

            // 3. DB에 먼저 저장 (Write-through)
            databaseStorage.save(post);

            // 4. DB 저장이 성공한 후에만 캐시 갱신
            cacheStorage.put(postId, newViewCount);

            return newViewCount;
        }
    }

    public Long getViewCount(Long postId) {
        Long viewCount = cacheStorage.get(postId);
        if (viewCount == null) {
            Post post = databaseStorage.findById(postId);
            return post != null ? post.getViewCount() : 0L;
        }
        return viewCount;
    }

    public Long getViewCountFromDb(Long postId) {
        Post post = databaseStorage.findById(postId);
        return post != null ? post.getViewCount() : 0L;
    }

    public void reset() {
        cacheStorage.clear();
        databaseStorage.clear();
        locks.clear();
    }
}
