# URL 모니터링 및 알림 시스템

Spring Boot 기반의 URL 모니터링 및 알림 시스템입니다. 대상 URL을 주기적으로 점검하고, 비정상 응답 시 다양한 채널로 알림을 전송합니다.

## 주요 기능

### 1. 수집 Agent
- 대상 URL을 주기적으로 점검 (응답 코드, 지연 시간, 타임스탬프 수집)
- 수집 결과를 백엔드의 `/events` API로 전송
- 네트워크 오류 시 자동 재시도 (지수 백오프)
- 전송 시도, 응답 코드, 실패 사유를 자체 로그로 기록
- 환경변수로 점검 주기, 대상 URL, 서버 주소, API 키 설정 가능

### 2. 백엔드 서버
- `/events` API로 헬스체크 데이터 수신 및 저장
- 비정상 응답 자동 감지 (상태 코드 >= 400 또는 응답시간 >= 5초)
- 알림 상태 관리 (Open, Ack, Resolved)
- 중복 알림 자동 방지
- 다중 통지 채널 지원
  - **콘솔 출력**: 로그에 알림 내용 출력
  - **웹훅**: HTTP POST로 알림 전송
  - **이메일**: SMTP를 통한 이메일 발송 (선택사항)
- NotificationLog 테이블에 모든 알림 발신 기록 저장

### 3. 알림 확인 방법

#### 발신 확인
- 서버 로그에서 `✓ Event sent successfully` 메시지 확인
- H2 Console에서 `NOTIFICATION_LOGS` 테이블 조회
  - `status`: SENT/FAILED/RETRY
  - `retry_count`: 재시도 횟수
  - `result_code`: HTTP 응답 코드
  - `result_message`: 상세 결과 메시지

#### 수신 확인
- **콘솔 알림**: 로그에 출력된 알림 박스 확인
- **웹훅 알림**: `/_test/inbox` API로 수신된 웹훅 조회
  ```bash
  curl http://localhost:8080/_test/inbox
  ```
- **이메일 알림**: 설정된 이메일 계정 또는 SMTP 테스트 서버 확인

## 시스템 구조

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│                 │         │                  │         │                 │
│  Monitoring     │──POST──▶│  Backend Server  │────────▶│  Notification   │
│  Agent          │         │  (/events)       │         │  Channels       │
│  (Scheduler)    │         │                  │         │                 │
│                 │         │  - EventService  │         │  - Console      │
│  30초마다 점검    │         │  - AlertService  │         │  - Webhook      │
│                 │         │  - Notification  │         │  - Email        │
└─────────────────┘         └──────────────────┘         └─────────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │   H2 Database    │
                            │                  │
                            │  - health_checks │
                            │  - alerts        │
                            │  - notification  │
                            │    _logs         │
                            │  - webhook_inbox │
                            └──────────────────┘
```

## 빠른 시작

### 1. 프로젝트 빌드

```bash
./gradlew build
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는

```bash
java -jar build/libs/Assignment-0.0.1-SNAPSHOT.jar
```

### 3. 실행 확인

애플리케이션이 시작되면 자동으로:
- 30초마다 설정된 URL (기본: https://www.google.com) 점검
- 비정상 응답 감지 시 알림 발생
- 콘솔에 알림 내용 출력
- 웹훅으로 알림 전송 (http://localhost:8080/_test/inbox)

## 설정

### application.properties

```properties
# 점검 주기 (밀리초)
monitoring.agent.check-interval=30000

# 점검할 대상 URL
monitoring.agent.target-url=https://www.google.com

# API 키
monitoring.agent.api-key=test-api-key-12345

# 상태 코드 임계값
monitoring.alert.status-code-threshold=400

# 응답 시간 임계값 (밀리초)
monitoring.alert.response-time-threshold=5000

# 웹훅 URL
monitoring.notification.webhook.url=http://localhost:8080/_test/inbox
```

### 환경변수로 설정 (선택사항)

```bash
export MONITORING_AGENT_TARGET_URL=https://example.com
export MONITORING_AGENT_CHECK_INTERVAL=60000
export MONITORING_API_KEY=your-secret-key
```

## API 엔드포인트

### 1. 헬스체크 이벤트 전송

```bash
POST /events
Content-Type: application/json

{
  "targetUrl": "https://example.com",
  "statusCode": 200,
  "responseTimeMs": 150,
  "timestamp": "2025-11-12T10:00:00",
  "apiKey": "test-api-key-12345"
}
```

### 2. 웹훅 수신 내역 조회

```bash
GET /_test/inbox
```

응답:
```json
{
  "success": true,
  "message": "Retrieved webhook inbox",
  "data": [
    {
      "id": 1,
      "payload": "{\"alert_id\":1,\"target_url\":\"https://example.com\",\"status\":\"OPEN\"}",
      "method": "POST",
      "receivedAt": "2025-11-12T10:01:00"
    }
  ]
}
```

### 3. H2 Database Console

- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:monitoring`
- Username: `sa`
- Password: (비워두기)

## 데이터베이스 테이블

### HEALTH_CHECKS
점검 결과 저장
- `id`: 기본 키
- `target_url`: 점검 대상 URL
- `status_code`: HTTP 상태 코드
- `response_time_ms`: 응답 시간 (밀리초)
- `timestamp`: 점검 시각
- `error_message`: 오류 메시지 (있는 경우)

### ALERTS
알림 이벤트 저장
- `id`: 기본 키
- `target_url`: 대상 URL
- `status`: 알림 상태 (OPEN, ACK, RESOLVED)
- `status_code`: HTTP 상태 코드
- `detected_at`: 감지 시각
- `acknowledged_at`: 확인 시각
- `resolved_at`: 해결 시각

### NOTIFICATION_LOGS
알림 발신 기록
- `id`: 기본 키
- `alert_id`: 알림 ID (외래 키)
- `channel`: 알림 채널 (CONSOLE, WEBHOOK, EMAIL)
- `status`: 전송 상태 (SENT, FAILED, RETRY)
- `attempted_at`: 시도 시각
- `retry_count`: 재시도 횟수
- `result_code`: 결과 코드
- `result_message`: 결과 메시지
- `message_id`: 메시지 ID

### WEBHOOK_INBOX
웹훅 수신 내역 (테스트용)
- `id`: 기본 키
- `payload`: 수신된 페이로드
- `method`: HTTP 메서드
- `headers`: 헤더 정보
- `received_at`: 수신 시각

## 테스트 시나리오

### 1. 정상 동작 확인

```bash
# 애플리케이션 실행
./gradlew bootRun

# 로그에서 다음 메시지 확인
# "Starting URL health check for: https://www.google.com"
# "Response received - Status: 200, Time: XXX ms"
# "Event sent successfully"
```

### 2. 비정상 응답 테스트

`application.properties`에서 대상 URL을 존재하지 않는 주소로 변경:

```properties
monitoring.agent.target-url=https://invalid-url-12345.com
```

또는 임계값을 낮춰서 정상 URL도 알림 발생:

```properties
monitoring.alert.response-time-threshold=1
```

예상 결과:
- 알림 생성 (ALERTS 테이블)
- 콘솔에 알림 박스 출력
- 웹훅 전송 (WEBHOOK_INBOX에 기록)
- NOTIFICATION_LOGS에 SENT 상태로 기록

### 3. 알림 수신 확인

```bash
# 웹훅 수신 내역 조회
curl http://localhost:8080/_test/inbox

# H2 Console에서 확인
# http://localhost:8080/h2-console
SELECT * FROM NOTIFICATION_LOGS;
SELECT * FROM WEBHOOK_INBOX;
```

### 4. 재시도 동작 확인

웹훅 URL을 잘못된 주소로 변경:

```properties
monitoring.notification.webhook.url=http://invalid-server:9999/webhook
```

예상 결과:
- 로그에 재시도 메시지 출력
- "Retrying in X seconds..."
- 최종 실패 후 NOTIFICATION_LOGS에 FAILED 상태로 기록

## 주요 로그 확인

### Agent 로그
```
=== Starting URL health check for: https://www.google.com ===
Sending request to: https://www.google.com
Response received - Status: 200, Time: 150 ms
[Attempt 1/3] Sending event to server: http://localhost:8080/events
✓ Event sent successfully - Status: 201
=== Health check completed ===
```

### Alert 로그
```
New alert created for URL: https://example.com - Alert ID: 1
Sending notifications for alert ID: 1
Attempting to send notification via: CONSOLE
Notification via CONSOLE - Status: SENT, Result: Successfully printed to console
```

### 콘솔 알림 예시
```
╔════════════════════════════════════════════════════════════╗
║              🚨 ALERT NOTIFICATION                        ║
╠════════════════════════════════════════════════════════════╣
║ Alert ID    : 1                                          ║
║ Target URL  : https://example.com                        ║
║ Status      : OPEN                                       ║
║ Status Code : 500                                        ║
║ Response    : 150 ms                                     ║
║ Detected At : 2025-11-12T10:00:00                        ║
║ Message     : Status code 500 is above threshold 400    ║
╚════════════════════════════════════════════════════════════╝
```

## 이메일 알림 설정 (선택사항)

로컬 SMTP 테스트 서버 사용 예시 (MailHog):

```bash
# MailHog 설치 (Docker)
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# application.properties 설정
monitoring.notification.email.enabled=true
monitoring.notification.email.to=test@example.com
spring.mail.host=localhost
spring.mail.port=1025

# 웹 UI에서 확인
# http://localhost:8025
```

## 문제 해결

### Agent가 동작하지 않는 경우
- `monitoring.agent.enabled=true` 확인
- `monitoring.agent.target-url` 설정 확인
- 로그에서 스케줄러 실행 메시지 확인

### 알림이 발생하지 않는 경우
- 임계값 설정 확인 (`monitoring.alert.*`)
- 대상 URL이 실제로 비정상 응답을 반환하는지 확인
- H2 Console에서 HEALTH_CHECKS 테이블 확인

### 웹훅이 전송되지 않는 경우
- `monitoring.notification.webhook.enabled=true` 확인
- 웹훅 URL이 올바른지 확인
- 네트워크 연결 확인

## 기술 스택

- Java 17
- Spring Boot 3.5.5
- Spring Data JPA
- H2 Database (인메모리)
- OkHttp (HTTP 클라이언트)
- Lombok
- Gradle

## 라이선스

MIT License
