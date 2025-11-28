package com.example.assignment.service;

import com.example.assignment.model.Post;
import com.example.assignment.storage.CacheStorage;
import com.example.assignment.storage.DatabaseStorage;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 게시글 ID별 락을 사용한 조회수 증가 서비스
 * 각 게시글마다 별도의 락을 사용하여 병렬성 향상
 * 장점: 서로 다른 게시글은 동시에 처리 가능
 * 단점: 락 관리 오버헤드, 같은 게시글은 여전히 순차 처리
 */
@Service
public class PerPostLockViewService {
    private final CacheStorage cacheStorage;
    private final DatabaseStorage databaseStorage;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public PerPostLockViewService(CacheStorage cacheStorage, DatabaseStorage databaseStorage) {
        this.cacheStorage = cacheStorage;
        this.databaseStorage = databaseStorage;
    }

    public Long incrementViewCount(Long postId) {
        Object lock = locks.computeIfAbsent(postId, k -> new Object());

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
        locks.clear();
    }
}
