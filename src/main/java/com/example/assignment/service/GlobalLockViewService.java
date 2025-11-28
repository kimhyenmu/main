package com.example.assignment.service;

import com.example.assignment.model.Post;
import com.example.assignment.storage.CacheStorage;
import com.example.assignment.storage.DatabaseStorage;
import org.springframework.stereotype.Service;

/**
 * 전역 락을 사용한 조회수 증가 서비스
 * 모든 연산에 synchronized를 적용하여 race condition 해결
 * 장점: 구현이 간단하고 확실히 동작
 * 단점: 성능 저하가 크고, 모든 게시글에 대해 순차 처리됨
 */
@Service
public class GlobalLockViewService {
    private final CacheStorage cacheStorage;
    private final DatabaseStorage databaseStorage;
    private final Object lock = new Object();

    public GlobalLockViewService(CacheStorage cacheStorage, DatabaseStorage databaseStorage) {
        this.cacheStorage = cacheStorage;
        this.databaseStorage = databaseStorage;
    }

    public Long incrementViewCount(Long postId) {
        synchronized (lock) {
            Long viewCount = cacheStorage.get(postId);

            if (viewCount == null) {
                Post post = databaseStorage.findById(postId);
                if (post == null) {
                    throw new IllegalArgumentException("Post not found: " + postId);
                }
                viewCount = post.getViewCount();
                cacheStorage.put(postId, viewCount);
            }

            Long newViewCount = viewCount + 1;

            Post post = databaseStorage.findById(postId);
            post.setViewCount(newViewCount);
            databaseStorage.save(post);

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
    }
}
