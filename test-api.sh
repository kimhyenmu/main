#!/bin/bash

echo "=== REST API vs GraphQL 비교 테스트 ==="
echo

# 애플리케이션이 시작될 때까지 대기
echo "애플리케이션 시작 대기 중..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8080/api/comparison/info > /dev/null 2>&1; then
        echo "애플리케이션이 시작되었습니다!"
        break
    fi
    echo "대기 중... ($i/30)"
    sleep 2
done

echo
echo "=== 1. REST API 테스트 ==="

echo "1-1. 게시글 목록 조회 (페이지네이션)"
curl -s "http://localhost:8080/api/posts?page=0&limit=5" | jq '.content[] | {id, title, createdAt}' | head -10

echo
echo "1-2. 검색 기능"
curl -s "http://localhost:8080/api/posts?search=Spring&limit=3" | jq '.content[] | {id, title}' | head -6

echo
echo "1-3. 사용자 목록 조회"
curl -s "http://localhost:8080/api/users?limit=3" | jq '.content[] | {id, name, email}' | head -9

echo
echo "=== 2. GraphQL 테스트 ==="

echo "2-1. 게시글 목록 조회 (필드 선택)"
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { posts(page: 0, limit: 3) { content { id title createdAt } totalElements } }"
  }' | jq '.data.posts.content[] | {id, title, createdAt}' | head -9

echo
echo "2-2. 사용자와 게시글 함께 조회 (DataLoader 사용)"
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { usersWithPosts { id name posts(limit: 2) { id title } } }"
  }' | jq '.data.usersWithPosts[0:2]' | head -15

echo
echo "=== 3. 성능 비교 ==="
echo "REST API 성능 테스트:"
curl -s "http://localhost:8080/api/comparison/rest-performance" | jq '{method, duration, userCount, totalQueries}' | head -5

echo
echo "테스트 완료!"
echo
echo "추가 테스트를 위한 URL:"
echo "- GraphiQL UI: http://localhost:8080/graphiql"
echo "- H2 Console: http://localhost:8080/h2-console"
echo "- API 정보: http://localhost:8080/api/comparison/info"