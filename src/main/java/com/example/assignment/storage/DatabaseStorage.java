package com.example.assignment.storage;

import com.example.assignment.model.Post;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * DB를 시뮬레이션하는 저장소
 * 실제 DB 대신 ConcurrentHashMap으로 구현
 */
@Component
public class DatabaseStorage {
    private final ConcurrentHashMap<Long, Post> database = new ConcurrentHashMap<>();

    public DatabaseStorage() {
        // 초기 데이터 생성
        database.put(1L, new Post(1L, "첫 번째 게시글", 0L));
        database.put(2L, new Post(2L, "두 번째 게시글", 0L));
        database.put(3L, new Post(3L, "세 번째 게시글", 0L));
    }

    public Post findById(Long postId) {
        Post post = database.get(postId);
        if (post == null) {
            return null;
        }
        // 실제 DB처럼 새 객체를 반환 (deep copy)
        return new Post(post.getId(), post.getTitle(), post.getViewCount());
    }

    public void save(Post post) {
        // 실제 DB 저장을 시뮬레이션 (약간의 지연)
        try {
            Thread.sleep(1); // DB 저장 지연 시뮬레이션
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        database.put(post.getId(), new Post(post.getId(), post.getTitle(), post.getViewCount()));
    }

    public void clear() {
        database.clear();
        // 초기 데이터 재생성
        database.put(1L, new Post(1L, "첫 번째 게시글", 0L));
        database.put(2L, new Post(2L, "두 번째 게시글", 0L));
        database.put(3L, new Post(3L, "세 번째 게시글", 0L));
    }
}
