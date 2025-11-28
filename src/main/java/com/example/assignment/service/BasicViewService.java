package com.example.assignment.service;

import com.example.assignment.model.Post;
import com.example.assignment.storage.CacheStorage;
import com.example.assignment.storage.DatabaseStorage;
import org.springframework.stereotype.Service;

/**
 * 문제가 있는 기본 조회수 증가 서비스
 * Race Condition과 타이밍 이슈가 발생하도록 의도적으로 작성
 */
@Service
public class BasicViewService {
    private final CacheStorage cacheStorage;
    private final DatabaseStorage databaseStorage;

    public BasicViewService(CacheStorage cacheStorage, DatabaseStorage databaseStorage) {
        this.cacheStorage = cacheStorage;
        this.databaseStorage = databaseStorage;
    }

    /**
     * 조회수 증가 로직 (문제 발생 버전)
     * 1. 캐시에서 조회수 읽기
     * 2. 캐시 값이 없으면 DB에서 읽어 캐시에 저장
     * 3. 조회수 +1 증가
     * 4. DB에 저장
     * 5. 캐시에 저장
     * 6. 최종 조회수 반환
     */
    public Long incrementViewCount(Long postId) {
        // (1) 캐시에서 조회수 읽기
        Long viewCount = cacheStorage.get(postId);

        // (2) 캐시 값이 없으면 DB에서 읽어 캐시에 저장
        if (viewCount == null) {
            Post post = databaseStorage.findById(postId);
            if (post == null) {
                throw new IllegalArgumentException("Post not found: " + postId);
            }
            viewCount = post.getViewCount();
            
            // 캐시 미스 타이밍 문제를 더 명확히 하기 위한 지연
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            cacheStorage.put(postId, viewCount);
        }

        // (3) 조회수 +1 증가
        Long newViewCount = viewCount + 1;

        // Race condition을 더 명확하게 하기 위한 지연
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // (4) DB에 저장
        Post post = databaseStorage.findById(postId);
        post.setViewCount(newViewCount);
        databaseStorage.save(post);

        // (5) 캐시에 저장
        cacheStorage.put(postId, newViewCount);

        // (6) 최종 조회수 반환
        return newViewCount;
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
