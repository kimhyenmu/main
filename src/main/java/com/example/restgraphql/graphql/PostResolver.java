package com.example.restgraphql.graphql;

import com.example.restgraphql.entity.Post;
import com.example.restgraphql.entity.User;
import com.example.restgraphql.graphql.dataloader.UserDataLoader;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostResolver {
    
    @SchemaMapping(typeName = "Post", field = "author")
    public CompletableFuture<User> getAuthor(Post post, DataFetchingEnvironment env) {
        log.debug("GraphQL - Resolving author for post: {}", post.getId());
        
        DataLoader<Long, User> userDataLoader = env.getDataLoader(UserDataLoader.DATA_LOADER_NAME);
        return userDataLoader.load(post.getAuthor().getId());
    }
}