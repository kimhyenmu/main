#!/bin/bash

# URL 모니터링 시스템 테스트 스크립트

echo "=================================="
echo "URL Monitoring System Test"
echo "=================================="
echo ""

# API 엔드포인트
EVENTS_API="http://localhost:8080/events"
INBOX_API="http://localhost:8080/_test/inbox"
API_KEY="test-api-key-12345"

# 1. 정상 응답 테스트
echo "1. 정상 응답 이벤트 전송..."
curl -X POST "$EVENTS_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"targetUrl\": \"https://www.google.com\",
    \"statusCode\": 200,
    \"responseTimeMs\": 150,
    \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S")\",
    \"apiKey\": \"$API_KEY\"
  }"
echo ""
echo ""

sleep 2

# 2. 비정상 응답 테스트 (상태 코드 500)
echo "2. 비정상 응답 이벤트 전송 (상태 코드 500)..."
curl -X POST "$EVENTS_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"targetUrl\": \"https://example-error.com\",
    \"statusCode\": 500,
    \"responseTimeMs\": 200,
    \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S")\",
    \"errorMessage\": \"Internal Server Error\",
    \"apiKey\": \"$API_KEY\"
  }"
echo ""
echo ""

sleep 2

# 3. 비정상 응답 테스트 (응답 시간 초과)
echo "3. 비정상 응답 이벤트 전송 (응답 시간 초과)..."
curl -X POST "$EVENTS_API" \
  -H "Content-Type: application/json" \
  -d "{
    \"targetUrl\": \"https://slow-website.com\",
    \"statusCode\": 200,
    \"responseTimeMs\": 8000,
    \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S")\",
    \"apiKey\": \"$API_KEY\"
  }"
echo ""
echo ""

sleep 2

# 4. 웹훅 수신 내역 확인
echo "4. 웹훅 수신 내역 조회..."
curl -X GET "$INBOX_API" | jq '.'
echo ""
echo ""

echo "=================================="
echo "테스트 완료!"
echo ""
echo "다음 단계:"
echo "1. H2 Console 접속: http://localhost:8080/h2-console"
echo "   - JDBC URL: jdbc:h2:mem:monitoring"
echo "   - Username: sa"
echo "   - Password: (비워두기)"
echo ""
echo "2. 다음 쿼리로 데이터 확인:"
echo "   SELECT * FROM HEALTH_CHECKS;"
echo "   SELECT * FROM ALERTS;"
echo "   SELECT * FROM NOTIFICATION_LOGS;"
echo "   SELECT * FROM WEBHOOK_INBOX;"
echo ""
echo "3. 서버 로그에서 알림 메시지 확인"
echo "=================================="
