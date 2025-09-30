# GraphQL 샘플 쿼리

GraphiQL UI (http://localhost:8080/graphiql)에서 사용할 수 있는 샘플 쿼리들입니다.

## 1. 게시글 목록 조회 (페이지네이션)

### 기본 필드만 조회
```graphql
query {
  posts(page: 0, limit: 5, sortBy: "createdAt", sortDir: "DESC") {
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

### 작성자 정보 포함
```graphql
query {
  posts(page: 0, limit: 5) {
    content {
      id
      title
      content
      createdAt
      author {
        id
        name
        email
      }
    }
  }
}
```

## 2. 검색 기능

```graphql
query {
  posts(search: "Spring", page: 0, limit: 10) {
    content {
      id
      title
      content
      author {
        name
      }
    }
    totalElements
  }
}
```

## 3. 사용자 목록 조회

### 기본 정보만
```graphql
query {
  users(page: 0, limit: 5) {
    content {
      id
      name
      email
      createdAt
    }
  }
}
```

### 게시글 포함
```graphql
query {
  users(page: 0, limit: 3) {
    content {
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
}
```

## 4. 관계형 데이터 조회 (DataLoader 사용)

### 모든 사용자와 각각의 게시글 3개씩
```graphql
query {
  usersWithPosts {
    id
    name
    email
    createdAt
    posts(limit: 3) {
      id
      title
      content
      createdAt
    }
  }
}
```

### 필요한 필드만 선택적으로 조회
```graphql
query {
  usersWithPosts {
    name
    posts(limit: 2) {
      title
    }
  }
}
```

## 5. 단일 조회

### 특정 게시글 조회
```graphql
query {
  post(id: "1") {
    id
    title
    content
    createdAt
    author {
      id
      name
      email
    }
  }
}
```

### 특정 사용자 조회
```graphql
query {
  user(id: "1") {
    id
    name
    email
    createdAt
    posts(limit: 5) {
      id
      title
      createdAt
    }
  }
}
```

## 6. 정렬 테스트

### 오름차순 정렬
```graphql
query {
  posts(page: 0, limit: 5, sortBy: "createdAt", sortDir: "ASC") {
    content {
      id
      title
      createdAt
    }
  }
}
```

### 제목순 정렬
```graphql
query {
  posts(page: 0, limit: 5, sortBy: "title", sortDir: "ASC") {
    content {
      id
      title
      createdAt
    }
  }
}
```

## REST API vs GraphQL 비교 포인트

1. **필드 선택**: GraphQL은 필요한 필드만 요청 가능
2. **단일 요청**: GraphQL은 하나의 요청으로 관련 데이터 모두 조회
3. **Over-fetching 방지**: 불필요한 데이터 전송 최소화
4. **Under-fetching 방지**: 여러 요청 없이 필요한 모든 데이터 한 번에 조회
5. **N+1 문제 해결**: DataLoader를 통한 배치 쿼리 최적화