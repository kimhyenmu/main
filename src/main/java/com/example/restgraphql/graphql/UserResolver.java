package com.example.restgraphql.graphql;

import com.example.restgraphql.entity.Post;
import com.example.restgraphql.entity.User;
import com.example.restgraphql.graphql.dataloader.PostDataLoader;
import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserResolver {
    
    @SchemaMapping(typeName = "User", field = "posts")
    public CompletableFuture<List<Post>> getPosts(User user, @Argument Integer limit, DataFetchingEnvironment env) {
        log.debug("GraphQL - Resolving posts for user: {}, limit: {}", user.getId(), limit);
        
        DataLoader<Long, List<Post>> postDataLoader = env.getDataLoader(PostDataLoader.DATA_LOADER_NAME);
        return postDataLoader.load(user.getId());
    }
}