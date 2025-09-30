package com.example.restgraphql.controller;

import com.example.restgraphql.dto.UserResponse;
import com.example.restgraphql.entity.User;
import com.example.restgraphql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comparison")
@RequiredArgsConstructor
@Slf4j
public class ComparisonController {
    
    private final UserRepository userRepository;
    
    @GetMapping("/rest-performance")
    public ResponseEntity<Map<String, Object>> testRestPerformance() {
        log.info("=== REST API Performance Test ===");
        
        long startTime = System.currentTimeMillis();
        
        // 1. 모든 사용자 조회
        List<User> users = userRepository.findAll();
        log.info("1. Users query executed");
        
        // 2. 각 사용자의 게시글 조회 (N+1 문제 발생)
        List<UserResponse> userResponses = users.stream().map(user -> {
            UserResponse response = UserResponse.from(user);
            log.debug("2. Posts query executed for user: {}", user.getId());
            return response;
        }).collect(Collectors.toList());
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("method", "REST API");
        result.put("duration", duration + "ms");
        result.put("userCount", users.size());
        result.put("totalQueries", users.size() + 1); // 1 for users + N for posts
        result.put("data", userResponses);
        
        log.info("REST API Performance: {}ms, {} queries", duration, users.size() + 1);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getComparisonInfo() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("description", "REST vs GraphQL Performance Comparison");
        info.put("endpoints", Map.of(
            "rest_performance", "/api/comparison/rest-performance",
            "graphql_endpoint", "/graphql",
            "graphiql_ui", "/graphiql"
        ));
        
        info.put("sample_queries", Map.of(
            "rest_posts", "GET /api/posts?page=0&limit=10&sortBy=createdAt&sortDir=desc",
            "rest_search", "GET /api/posts?search=Spring&page=0&limit=10",
            "rest_users_with_posts", "GET /api/users/with-posts",
            "graphql_posts", """
                query {
                  posts(page: 0, limit: 10, sortBy: "createdAt", sortDir: "DESC") {
                    content {
                      id
                      title
                      createdAt
                    }
                    totalElements
                  }
                }
                """,
            "graphql_search", """
                query {
                  posts(search: "Spring", page: 0, limit: 10) {
                    content {
                      id
                      title
                      content
                      author {
                        name
                      }
                    }
                  }
                }
                """,
            "graphql_users_with_posts", """
                query {
                  usersWithPosts {
                    id
                    name
                    email
                    posts(limit: 3) {
                      id
                      title
                      createdAt
                    }
                  }
                }
                """
        ));
        
        return ResponseEntity.ok(info);
    }
}