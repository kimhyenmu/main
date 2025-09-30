package com.example.restgraphql.config;

import com.example.restgraphql.entity.Post;
import com.example.restgraphql.entity.User;
import com.example.restgraphql.repository.PostRepository;
import com.example.restgraphql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    
    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("Initializing sample data...");
            
            // 사용자 생성
            List<User> users = createUsers();
            userRepository.saveAll(users);
            
            // 게시글 생성
            List<Post> posts = createPosts(users);
            postRepository.saveAll(posts);
            
            log.info("Sample data initialized: {} users, {} posts", users.size(), posts.size());
        }
    }
    
    private List<User> createUsers() {
        List<User> users = new ArrayList<>();
        String[] names = {
            "김철수", "이영희", "박민수", "최지영", "정현우",
            "한소영", "임태호", "윤미정", "강동현", "조은비",
            "신재원", "오수진", "배준호", "서나영", "홍길동"
        };
        
        for (int i = 0; i < names.length; i++) {
            User user = User.builder()
                    .name(names[i])
                    .email(names[i].toLowerCase().replaceAll("[가-힣]", "") + (i + 1) + "@example.com")
                    .createdAt(LocalDateTime.now().minusDays(new Random().nextInt(30)))
                    .build();
            users.add(user);
        }
        
        return users;
    }
    
    private List<Post> createPosts(List<User> users) {
        List<Post> posts = new ArrayList<>();
        String[] titles = {
            "Spring Boot 시작하기",
            "GraphQL vs REST API 비교",
            "JPA N+1 문제 해결하기",
            "DataLoader 사용법",
            "페이지네이션 구현하기",
            "검색 기능 최적화",
            "데이터베이스 인덱싱",
            "캐싱 전략",
            "마이크로서비스 아키텍처",
            "도커 컨테이너화",
            "쿠버네티스 배포",
            "CI/CD 파이프라인",
            "테스트 주도 개발",
            "클린 코드 작성법",
            "디자인 패턴 활용",
            "성능 최적화 기법",
            "보안 모범 사례",
            "모니터링과 로깅",
            "에러 핸들링",
            "코드 리뷰 가이드"
        };
        
        String[] contents = {
            "Spring Boot는 Java 기반의 웹 애플리케이션을 빠르게 개발할 수 있게 해주는 프레임워크입니다.",
            "GraphQL과 REST API의 장단점을 비교하고 언제 어떤 것을 사용해야 하는지 알아봅시다.",
            "JPA에서 발생하는 N+1 문제를 해결하는 다양한 방법들을 소개합니다.",
            "GraphQL에서 DataLoader를 사용하여 성능을 최적화하는 방법을 설명합니다.",
            "대용량 데이터를 효율적으로 처리하기 위한 페이지네이션 구현 방법입니다.",
            "검색 기능의 성능을 향상시키는 다양한 최적화 기법들을 다룹니다.",
            "데이터베이스 성능 향상을 위한 인덱스 설계와 최적화 방법입니다.",
            "애플리케이션 성능 향상을 위한 다양한 캐싱 전략을 소개합니다.",
            "마이크로서비스 아키텍처의 설계 원칙과 구현 방법을 설명합니다.",
            "애플리케이션을 도커 컨테이너로 패키징하는 방법을 알아봅시다.",
            "쿠버네티스를 사용한 컨테이너 오케스트레이션과 배포 전략입니다.",
            "지속적 통합과 배포를 위한 CI/CD 파이프라인 구축 방법입니다.",
            "TDD(Test Driven Development) 방법론과 실제 적용 사례를 다룹니다.",
            "유지보수하기 좋은 클린 코드를 작성하는 원칙과 방법들입니다.",
            "소프트웨어 개발에서 자주 사용되는 디자인 패턴들을 소개합니다.",
            "애플리케이션 성능을 향상시키는 다양한 최적화 기법들을 설명합니다.",
            "웹 애플리케이션 보안을 위한 모범 사례와 취약점 대응 방법입니다.",
            "시스템 모니터링과 효과적인 로깅 전략에 대해 알아봅시다.",
            "예외 상황을 우아하게 처리하는 에러 핸들링 방법들을 다룹니다.",
            "효과적인 코드 리뷰를 위한 가이드라인과 체크리스트입니다."
        };
        
        Random random = new Random();
        
        // 각 사용자마다 3-7개의 게시글 생성
        for (User user : users) {
            int postCount = 3 + random.nextInt(5); // 3-7개
            
            for (int i = 0; i < postCount; i++) {
                int titleIndex = random.nextInt(titles.length);
                Post post = Post.builder()
                        .title(titles[titleIndex])
                        .content(contents[titleIndex])
                        .author(user)
                        .createdAt(LocalDateTime.now().minusDays(random.nextInt(30))
                                .minusHours(random.nextInt(24))
                                .minusMinutes(random.nextInt(60)))
                        .build();
                posts.add(post);
            }
        }
        
        return posts;
    }
}