package com.example.restgraphql.graphql.dataloader;

import com.example.restgraphql.entity.Post;
import com.example.restgraphql.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.BatchLoader;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostDataLoader implements BatchLoader<Long, List<Post>> {
    
    public static final String DATA_LOADER_NAME = "postDataLoader";
    
    private final PostRepository postRepository;
    
    @Override
    public CompletionStage<List<List<Post>>> load(List<Long> authorIds) {
        log.info("DataLoader - Batch loading posts for authors: {}", authorIds);
        
        return CompletableFuture.supplyAsync(() -> {
            // 모든 작성자의 게시글을 한 번의 쿼리로 가져옴
            List<Post> allPosts = postRepository.findByAuthorIdIn(authorIds);
            
            // 작성자별로 게시글을 그룹화하고 최신 3개만 선택
            Map<Long, List<Post>> postsByAuthor = allPosts.stream()
                    .collect(Collectors.groupingBy(post -> post.getAuthor().getId()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream()
                                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                                    .limit(3)
                                    .collect(Collectors.toList())
                    ));
            
            // authorIds 순서대로 결과 반환
            return authorIds.stream()
                    .map(authorId -> postsByAuthor.getOrDefault(authorId, List.of()))
                    .collect(Collectors.toList());
        });
    }
}