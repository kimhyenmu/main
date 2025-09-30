package com.example.restgraphql.graphql;

import com.example.restgraphql.dto.PageResponse;
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
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class QueryResolver {
    
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    
    @QueryMapping
    public PageResponse<Post> posts(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String search,
            @Argument String sortBy,
            @Argument String sortDir) {
        
        log.info("GraphQL - Getting posts: page={}, limit={}, search={}, sortBy={}, sortDir={}", 
                page, limit, search, sortBy, sortDir);
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, limit, sort);
        
        Page<Post> posts;
        if (search != null && !search.trim().isEmpty()) {
            posts = postRepository.findByKeyword(search, pageable);
        } else {
            posts = postRepository.findAll(pageable);
        }
        
        return PageResponse.from(posts);
    }
    
    @QueryMapping
    public Post post(@Argument String id) {
        log.info("GraphQL - Getting post by id: {}", id);
        return postRepository.findById(Long.valueOf(id))
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }
    
    @QueryMapping
    public PageResponse<User> users(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String search,
            @Argument String sortBy,
            @Argument String sortDir) {
        
        log.info("GraphQL - Getting users: page={}, limit={}, search={}, sortBy={}, sortDir={}", 
                page, limit, search, sortBy, sortDir);
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, limit, sort);
        
        Page<User> users;
        if (search != null && !search.trim().isEmpty()) {
            users = userRepository.findByKeyword(search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        
        return PageResponse.from(users);
    }
    
    @QueryMapping
    public User user(@Argument String id) {
        log.info("GraphQL - Getting user by id: {}", id);
        return userRepository.findById(Long.valueOf(id))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    @QueryMapping
    public List<User> usersWithPosts() {
        log.info("GraphQL - Getting all users with posts");
        return userRepository.findAll();
    }
}