# REST API vs GraphQL 비교 프로젝트

Spring Boot를 사용하여 REST API와 GraphQL의 차이점을 비교하고 성능을 분석하는 프로젝트입니다.

## 주요 기능

### 1. 게시글 목록 조회 (페이지네이션)
- **REST API**: `/api/posts?page=0&limit=10` 형태로 항상 동일한 필드 반환
- **GraphQL**: 클라이언트가 원하는 필드만 선택적으로 요청 가능

### 2. 정렬 기능
- **REST API**: 쿼리 파라미터로 정렬 기준과 순서 지정 (`sortBy=createdAt&sortDir=desc`)
- **GraphQL**: 쿼리 인자로 정렬 기준과 순서 지정

### 3. 검색 기능
- **REST API**: 쿼리 파라미터로 검색어 전달 (`search=keyword`)
- **GraphQL**: 검색어를 인자로 받는 쿼리

### 4. 관계형 데이터 조회
- **REST API**: 여러 엔드포인트 호출 필요 (N+1 문제 발생)
- **GraphQL**: 하나의 쿼리로 사용자와 게시글을 동시에 조회, DataLoader로 N+1 문제 해결

## 시작하기

### 1. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2. 접속 URL
- **H2 Database Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: `password`

- **GraphiQL UI**: http://localhost:8080/graphiql
- **REST API 비교 정보**: http://localhost:8080/api/comparison/info

## API 사용 예시

### REST API 예시

#### 1. 게시글 목록 조회 (페이지네이션)
```bash
GET /api/posts?page=0&limit=10&sortBy=createdAt&sortDir=desc
```

#### 2. 검색 기능
```bash
GET /api/posts?search=Spring&page=0&limit=10
```

#### 3. 사용자와 게시글 함께 조회
```bash
GET /api/users/with-posts
```

### GraphQL 예시

#### 1. 게시글 목록 조회 (필드 선택)
```graphql
query {
  posts(page: 0, limit: 10, sortBy: "createdAt", sortDir: "DESC") {
    content {
      id
      title
      createdAt
    }
    totalElements
    totalPages
  }
}
```

#### 2. 검색 기능
```graphql
query {
  posts(search: "Spring", page: 0, limit: 10) {
    content {
      id
      title
      content
      author {
        name
        email
      }
    }
  }
}
```

#### 3. 사용자와 게시글 함께 조회 (DataLoader 사용)
```graphql
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
```

## 성능 비교

### N+1 문제 해결
- **REST API**: N+1 문제 발생 (사용자 수만큼 추가 쿼리 실행)
- **GraphQL**: DataLoader를 사용하여 배치 쿼리로 해결

### 네트워크 요청 비교
1. **REST API**: 여러 엔드포인트 호출 필요
   - 사용자 목록: `GET /api/users`
   - 각 사용자의 게시글: `GET /api/users/{id}/posts`

2. **GraphQL**: 단일 쿼리로 모든 데이터 조회
   - 하나의 요청으로 사용자와 게시글 정보 모두 획득

### 응답 크기 비교
- **REST API**: 고정된 필드 구조로 불필요한 데이터도 전송
- **GraphQL**: 클라이언트가 요청한 필드만 전송하여 응답 크기 최적화

## 프로젝트 구조

```
src/main/java/com/example/restgraphql/
├── config/                 # 설정 클래스
│   ├── DataInitializer.java
│   ├── GraphQLConfig.java
│   └── GraphQLScalarConfig.java
├── controller/             # REST API 컨트롤러
│   ├── PostController.java
│   ├── UserController.java
│   └── ComparisonController.java
├── dto/                    # 데이터 전송 객체
│   ├── PostResponse.java
│   ├── UserResponse.java
│   └── PageResponse.java
├── entity/                 # JPA 엔티티
│   ├── Post.java
│   └── User.java
├── graphql/               # GraphQL 관련
│   ├── QueryResolver.java
│   ├── PostResolver.java
│   ├── UserResolver.java
│   └── dataloader/
│       ├── PostDataLoader.java
│       └── UserDataLoader.java
└── repository/            # JPA 리포지토리
    ├── PostRepository.java
    └── UserRepository.java
```

## 기술 스택

- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **Spring GraphQL**
- **H2 Database**
- **Lombok**
- **Java DataLoader**

## 학습 포인트

1. **REST API vs GraphQL 차이점 이해**
2. **N+1 문제와 DataLoader를 통한 해결**
3. **페이지네이션과 정렬 구현**
4. **검색 기능 최적화**
5. **성능 모니터링과 비교 분석**