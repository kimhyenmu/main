package com.example.restgraphql.controller;

import com.example.restgraphql.dto.PageResponse;
import com.example.restgraphql.dto.PostResponse;
import com.example.restgraphql.entity.Post;
import com.example.restgraphql.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {
    
    private final PostRepository postRepository;
    
    @GetMapping
    public PageResponse<PostResponse> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        log.info("REST API - Getting posts: page={}, limit={}, search={}, sortBy={}, sortDir={}", 
                page, limit, search, sortBy, sortDir);
        
        Sort sort = sortDir.equalsIgnoreCase("asc") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, limit, sort);
        
        Page<Post> posts;
        if (search != null && !search.trim().isEmpty()) {
            posts = postRepository.findByKeyword(search, pageable);
        } else {
            posts = postRepository.findAll(pageable);
        }
        
        Page<PostResponse> postResponses = posts.map(PostResponse::from);
        return PageResponse.from(postResponses);
    }
    
    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable Long id) {
        log.info("REST API - Getting post by id: {}", id);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return PostResponse.from(post);
    }
}