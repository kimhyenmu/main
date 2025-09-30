package com.example.restgraphql.graphql.dataloader;

import com.example.restgraphql.entity.User;
import com.example.restgraphql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.BatchLoader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDataLoader implements BatchLoader<Long, User> {
    
    public static final String DATA_LOADER_NAME = "userDataLoader";
    
    private final UserRepository userRepository;
    
    @Override
    public CompletionStage<List<User>> load(List<Long> userIds) {
        log.info("DataLoader - Batch loading users: {}", userIds);
        
        return CompletableFuture.supplyAsync(() -> {
            List<User> users = userRepository.findByIdIn(userIds);
            
            // userIds 순서대로 정렬하여 반환
            Map<Long, User> userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            
            return userIds.stream()
                    .map(userMap::get)
                    .collect(Collectors.toList());
        });
    }
}