package com.example.restgraphql.config;

import com.example.restgraphql.graphql.dataloader.PostDataLoader;
import com.example.restgraphql.graphql.dataloader.UserDataLoader;
import lombok.RequiredArgsConstructor;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.boot.autoconfigure.graphql.GraphQlWebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataLoaderRegistrar;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@RequiredArgsConstructor
public class GraphQLConfig implements GraphQlWebMvcConfigurer {
    
    private final UserDataLoader userDataLoader;
    private final PostDataLoader postDataLoader;
    
    @Bean
    public DataLoaderRegistrar dataLoaderRegistrar() {
        return registry -> {
            DataLoader<Long, ?> userLoader = DataLoader.newDataLoader(userDataLoader);
            DataLoader<Long, ?> postLoader = DataLoader.newDataLoader(postDataLoader);
            
            registry.register(UserDataLoader.DATA_LOADER_NAME, userLoader);
            registry.register(PostDataLoader.DATA_LOADER_NAME, postLoader);
        };
    }
    
    @Bean
    public WebGraphQlInterceptor loggingInterceptor() {
        return new WebGraphQlInterceptor() {
            @Override
            public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
                long startTime = System.currentTimeMillis();
                
                return chain.next(request)
                        .doOnNext(response -> {
                            long duration = System.currentTimeMillis() - startTime;
                            System.out.println("GraphQL Query executed in " + duration + "ms");
                            System.out.println("Query: " + request.getDocument());
                        });
            }
        };
    }
}