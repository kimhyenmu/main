package com.example.restgraphql.controller;

import com.example.restgraphql.dto.PageResponse;
import com.example.restgraphql.dto.PostResponse;
import com.example.restgraphql.dto.UserResponse;
import com.example.restgraphql.entity.Post;
import com.example.restgraphql.entity.User;
import com.example.restgraphql.repository.PostRepository;
import com.example.restgraphql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    
    @GetMapping
    public PageResponse<UserResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        log.info("REST API - Getting users: page={}, limit={}, search={}, sortBy={}, sortDir={}", 
                page, limit, search, sortBy, sortDir);
        
        Sort sort = sortDir.equalsIgnoreCase("asc") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, limit, sort);
        
        Page<User> users;
        if (search != null && !search.trim().isEmpty()) {
            users = userRepository.findByKeyword(search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        
        Page<UserResponse> userResponses = users.map(UserResponse::from);
        return PageResponse.from(userResponses);
    }
    
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        log.info("REST API - Getting user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return UserResponse.from(user);
    }
    
    @GetMapping("/{id}/posts")
    public PageResponse<PostResponse> getUserPosts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        log.info("REST API - Getting posts for user {}: page={}, limit={}, sortBy={}, sortDir={}", 
                id, page, limit, sortBy, sortDir);
        
        Sort sort = sortDir.equalsIgnoreCase("asc") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, limit, sort);
        
        Page<Post> posts = postRepository.findByAuthorId(id, pageable);
        Page<PostResponse> postResponses = posts.map(PostResponse::from);
        return PageResponse.from(postResponses);
    }
    
    // 관계형 데이터 조회를 위한 엔드포인트 - 모든 사용자와 각각의 게시글 3개씩
    @GetMapping("/with-posts")
    public List<UserResponse> getUsersWithPosts() {
        log.info("REST API - Getting all users with their top 3 posts");
        
        List<User> users = userRepository.findAll();
        
        return users.stream().map(user -> {
            // 각 사용자마다 별도의 쿼리 실행 (N+1 문제 발생)
            List<Post> posts = postRepository.findTop3ByAuthorIdOrderByCreatedAtDesc(
                    user.getId(), PageRequest.of(0, 3));
            
            return UserResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .createdAt(user.getCreatedAt())
                    .posts(posts.stream().map(post -> PostResponse.builder()
                            .id(post.getId())
                            .title(post.getTitle())
                            .content(post.getContent())
                            .createdAt(post.getCreatedAt())
                            .build()).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }
}