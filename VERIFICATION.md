# URL 모니터링 시스템 검증 가이드

이 문서는 URL 모니터링 및 알림 시스템의 모든 요구사항이 구현되었고 정상적으로 작동한다는 것을 검증하는 방법을 설명합니다.

## 요구사항 체크리스트

### ✅ 1. 수집 Agent

#### 구현 내용
- [x] 대상 URL을 주기적으로 점검 (응답 코드, 지연 시간, 타임스탬프 수집)
  - 파일: `UrlMonitoringAgent.java`
  - 설정: `monitoring.agent.check-interval` (기본 30초)
  
- [x] 수집 결과를 백엔드의 /events API로 전송
  - 메서드: `sendEventToServer()` in `UrlMonitoringAgent.java`
  
- [x] 네트워크 오류 시 재시도 포함
  - 최대 재시도: `monitoring.agent.max-retries` (기본 3회)
  - 지수 백오프 적용
  
- [x] Agent는 전송 시도, 응답 코드, 실패 사유를 자체 로그로 남김
  - 로그 예시:
    ```
    [Attempt 1/3] Sending event to server: http://localhost:8080/events
    ✓ Event sent successfully - Status: 201, Response: {...}
    ```
  
- [x] 환경변수로 점검 주기, 대상 URL, 서버 주소, API 키 설정 가능
  - `monitoring.agent.check-interval`
  - `monitoring.agent.target-url`
  - `monitoring.agent.server-url`
  - `monitoring.agent.api-key`

#### 검증 방법

1. 애플리케이션 실행:
```bash
./gradlew bootRun
```

2. 로그에서 다음 메시지 확인:
```
=== Starting URL health check for: https://www.google.com ===
Sending request to: https://www.google.com
Response received - Status: 200, Time: XXX ms
[Attempt 1/3] Sending event to server: http://localhost:8080/events
✓ Event sent successfully - Status: 201
=== Health check completed ===
```

3. 재시도 동작 확인 (웹훅 URL을 잘못된 주소로 변경):
```properties
monitoring.notification.webhook.url=http://invalid-server:9999/webhook
```

예상 로그:
```
✗ Failed to send event (Attempt 1/3): Connection refused
Retrying in 2 seconds...
✗ Failed to send event (Attempt 2/3): Connection refused
Retrying in 4 seconds...
```

---

### ✅ 2. 백엔드 서버

#### 구현 내용

- [x] /events로 들어온 데이터를 검증·저장
  - 컨트롤러: `EventController.java`
  - 서비스: `EventProcessingService.java`
  - 엔티티: `HealthCheck.java`
  - 검증: `@Valid` + Bean Validation

- [x] 비정상 응답을 감지하면 알림 이벤트를 생성
  - 서비스: `AlertService.java`
  - 감지 조건:
    - 상태 코드 >= 400 (설정: `monitoring.alert.status-code-threshold`)
    - 응답 시간 >= 5000ms (설정: `monitoring.alert.response-time-threshold`)

- [x] 중복 알림을 방지
  - 구현: `AlertService.handleAbnormalResponse()`
  - 동일 URL에 대해 OPEN 상태의 알림이 이미 존재하면 새 알림을 생성하지 않음

- [x] 알림 상태(Open, Ack, Resolved)를 관리
  - 엔티티: `Alert.AlertStatus` enum
  - OPEN: 새로 발생한 알림
  - ACK: 확인됨
  - RESOLVED: 해결됨 (정상 응답 수신 시 자동 해결)

- [x] 통지 채널 1개 이상을 구현
  - 콘솔 출력: `ConsoleNotificationChannel.java` ✓
  - 웹훅: `WebhookNotificationChannel.java` ✓
  - 이메일: `EmailNotificationChannel.java` ✓

- [x] 통지 시도 결과를 NotificationLog로 남김
  - 엔티티: `NotificationLog.java`
  - 필드: `attempted_at`, `channel`, `status`, `result_code`, `result_message`, `message_id`, `retry_count`

#### 검증 방법

##### 2-1. API 데이터 수신 및 저장 확인

```bash
# 테스트 이벤트 전송
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://test.com",
    "statusCode": 200,
    "responseTimeMs": 150,
    "timestamp": "2025-11-12T10:00:00",
    "apiKey": "test-api-key-12345"
  }'

# 예상 응답:
# {
#   "success": true,
#   "message": "Event processed successfully",
#   "data": 1
# }
```

H2 Console에서 확인:
```sql
SELECT * FROM HEALTH_CHECKS;
```

##### 2-2. 알림 감지 및 생성 확인

```bash
# 비정상 응답 이벤트 전송
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://error.com",
    "statusCode": 500,
    "responseTimeMs": 200,
    "timestamp": "2025-11-12T10:01:00",
    "errorMessage": "Internal Server Error",
    "apiKey": "test-api-key-12345"
  }'
```

예상 로그:
```
New alert created for URL: https://error.com - Alert ID: 1
Sending notifications for alert ID: 1
```

H2 Console에서 확인:
```sql
SELECT * FROM ALERTS WHERE target_url = 'https://error.com';
-- status가 'OPEN'이어야 함
```

##### 2-3. 중복 알림 방지 확인

동일한 URL에 대해 비정상 응답을 다시 전송:
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://error.com",
    "statusCode": 503,
    "responseTimeMs": 300,
    "timestamp": "2025-11-12T10:02:00",
    "apiKey": "test-api-key-12345"
  }'
```

예상 로그:
```
Alert already exists for URL: https://error.com. Skipping duplicate alert.
```

H2 Console에서 확인:
```sql
SELECT COUNT(*) FROM ALERTS WHERE target_url = 'https://error.com' AND status = 'OPEN';
-- 결과: 1 (중복 생성되지 않음)
```

##### 2-4. 알림 해결(Resolved) 확인

정상 응답 전송:
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://error.com",
    "statusCode": 200,
    "responseTimeMs": 100,
    "timestamp": "2025-11-12T10:03:00",
    "apiKey": "test-api-key-12345"
  }'
```

예상 로그:
```
Alert resolved for URL: https://error.com - Alert ID: 1
```

H2 Console에서 확인:
```sql
SELECT * FROM ALERTS WHERE id = 1;
-- status가 'RESOLVED'로 변경됨
-- resolved_at이 설정됨
```

---

### ✅ 3. 알림 보냄(발신) 확인 방법

#### 3-1. 서버 로그 확인

애플리케이션 로그에서 다음 메시지 확인:

```
Sending notifications for alert ID: 1
Attempting to send notification via: CONSOLE
Notification via CONSOLE - Status: SENT, Result: Successfully printed to console
Attempting to send notification via: WEBHOOK
Webhook response: 200 - {"success":true,...}
Notification via WEBHOOK - Status: SENT, Result: Webhook sent successfully
```

#### 3-2. NotificationLog 테이블 확인

```sql
SELECT 
    id,
    alert_id,
    channel,
    status,
    attempted_at,
    retry_count,
    result_code,
    result_message,
    message_id
FROM NOTIFICATION_LOGS
ORDER BY attempted_at DESC;
```

예상 결과:
```
| id | alert_id | channel | status | result_code | result_message |
|----|----------|---------|--------|-------------|----------------|
| 1  | 1        | CONSOLE | SENT   | 200         | Successfully printed to console |
| 2  | 1        | WEBHOOK | SENT   | 200         | Webhook sent successfully: ... |
```

#### 3-3. 실패 및 재시도 확인

웹훅 URL을 잘못된 주소로 변경한 후 비정상 응답 전송:

```properties
monitoring.notification.webhook.url=http://invalid-server:9999/webhook
```

예상 로그:
```
✗ Failed to send notification via WEBHOOK
Exception: Connection refused
```

NotificationLog 확인:
```sql
SELECT * FROM NOTIFICATION_LOGS WHERE status = 'FAILED';
```

예상 결과:
```
| channel | status | result_code | result_message |
|---------|--------|-------------|----------------|
| WEBHOOK | FAILED | 0           | Exception: Connection refused |
```

---

### ✅ 4. 알림 도착(수신) 확인 방법

#### 4-1. 콘솔형 알림 확인

애플리케이션 로그에서 알림 박스 확인:

```
╔════════════════════════════════════════════════════════════╗
║              🚨 ALERT NOTIFICATION                        ║
╠════════════════════════════════════════════════════════════╣
║ Alert ID    : 1                                          ║
║ Target URL  : https://error.com                          ║
║ Status      : OPEN                                       ║
║ Status Code : 500                                        ║
║ Response    : 200 ms                                     ║
║ Detected At : 2025-11-12T10:01:00                        ║
║ Message     : Status code 500 is above threshold 400    ║
╚════════════════════════════════════════════════════════════╝
```

**증명**: 이 로그 출력이 "알림이 실제로 도착했음"을 보여줍니다.

#### 4-2. 웹훅 수신 확인

##### 방법 1: 웹훅 수신 내역 API 조회

```bash
curl http://localhost:8080/_test/inbox
```

예상 응답:
```json
{
  "success": true,
  "message": "Retrieved webhook inbox",
  "data": [
    {
      "id": 1,
      "payload": "{\"alert_id\":1,\"target_url\":\"https://error.com\",\"status\":\"OPEN\",\"status_code\":500,...}",
      "method": "POST",
      "headers": "{...}",
      "receivedAt": "2025-11-12T10:01:05"
    }
  ],
  "timestamp": "2025-11-12T10:05:00"
}
```

**증명**: `payload` 필드에 알림 데이터가 포함되어 있고, `receivedAt`에 수신 시각이 기록됨.

##### 방법 2: H2 Console에서 확인

```sql
SELECT * FROM WEBHOOK_INBOX ORDER BY received_at DESC;
```

예상 결과:
```
| id | payload                                    | method | received_at          |
|----|--------------------------------------------|--------|----------------------|
| 1  | {"alert_id":1,"target_url":"https://...} | POST   | 2025-11-12 10:01:05 |
```

##### 방법 3: 서버 로그 확인

```
=== WEBHOOK RECEIVED ===
Method: POST
Headers: {content-type=application/json, user-agent=URL-Monitor/1.0, ...}
Body: {"alert_id":1,"target_url":"https://error.com","status":"OPEN",...}
========================
```

**증명**: 이 로그들이 "웹훅이 실제로 수신되었음"을 증명합니다.

#### 4-3. 이메일 알림 확인 (선택사항)

##### 로컬 SMTP 서버 사용 (MailHog)

1. MailHog 실행:
```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

2. `application.properties` 설정:
```properties
monitoring.notification.email.enabled=true
monitoring.notification.email.to=test@example.com
spring.mail.host=localhost
spring.mail.port=1025
```

3. 비정상 응답 전송 후 MailHog UI 확인:
   - http://localhost:8025
   - 수신된 이메일 목록에서 알림 메일 확인

**증명**: MailHog UI에 표시된 이메일이 "이메일이 실제로 도착했음"을 증명합니다.

---

## 전체 시스템 통합 테스트

### 테스트 시나리오 1: 정상 동작

```bash
# 1. 애플리케이션 시작
./gradlew bootRun

# 2. 30초 대기 (Agent가 자동으로 URL 점검)

# 3. 로그 확인
# - Agent가 URL 점검
# - /events API로 데이터 전송
# - 데이터 저장 완료
```

예상 로그:
```
=== Starting URL health check for: https://www.google.com ===
Response received - Status: 200, Time: 150 ms
✓ Event sent successfully - Status: 201
```

### 테스트 시나리오 2: 비정상 응답 및 알림 발생

```bash
# 1. application.properties 수정
monitoring.agent.target-url=https://httpstat.us/500

# 2. 애플리케이션 재시작

# 3. 30초 대기

# 4. 콘솔에서 알림 박스 확인
# 5. 웹훅 수신 내역 확인
curl http://localhost:8080/_test/inbox

# 6. H2 Console에서 데이터 확인
# http://localhost:8080/h2-console
SELECT * FROM ALERTS;
SELECT * FROM NOTIFICATION_LOGS;
```

### 테스트 시나리오 3: 자동 재시도 동작 확인

```bash
# 테스트 스크립트 실행
chmod +x test-monitoring.sh
./test-monitoring.sh
```

---

## 요구사항 검증 요약

| 요구사항 | 구현 위치 | 검증 방법 | 상태 |
|---------|----------|----------|------|
| 대상 URL 주기적 점검 | `UrlMonitoringAgent.java` | 로그, H2 Console | ✅ |
| /events API로 전송 | `UrlMonitoringAgent.sendEventToServer()` | 로그, API 응답 | ✅ |
| 네트워크 오류 시 재시도 | `UrlMonitoringAgent.sendEventToServer()` | 로그 (재시도 메시지) | ✅ |
| Agent 자체 로깅 | `UrlMonitoringAgent.java` | 로그 파일/콘솔 | ✅ |
| 환경변수 설정 | `application.properties` | 설정 변경 후 동작 확인 | ✅ |
| 데이터 검증·저장 | `EventController`, `EventProcessingService` | H2 Console (HEALTH_CHECKS) | ✅ |
| 비정상 응답 감지 | `AlertService.detectAndProcessAlert()` | H2 Console (ALERTS) | ✅ |
| 중복 알림 방지 | `AlertService.handleAbnormalResponse()` | 로그, H2 Console | ✅ |
| 알림 상태 관리 | `Alert.AlertStatus` | H2 Console (status 컬럼) | ✅ |
| 통지 채널 구현 | `ConsoleNotificationChannel`, `WebhookNotificationChannel`, `EmailNotificationChannel` | 로그, 웹훅 수신, 이메일 | ✅ |
| NotificationLog 기록 | `NotificationService.sendNotifications()` | H2 Console (NOTIFICATION_LOGS) | ✅ |
| 발신 확인 | `NOTIFICATION_LOGS` 테이블 | SQL 쿼리 | ✅ |
| 수신 확인 - 콘솔 | 로그 출력 | 콘솔 로그 | ✅ |
| 수신 확인 - 웹훅 | `/_test/inbox` API | API 조회, H2 Console (WEBHOOK_INBOX) | ✅ |
| 수신 확인 - 이메일 | 이메일 발송 | MailHog UI (선택사항) | ✅ |

---

## 결론

모든 요구사항이 구현되었고, 각 기능은 위에 명시된 방법으로 검증 가능합니다.

### 핵심 증명 포인트

1. **알림 발신 확인**:
   - `NOTIFICATION_LOGS` 테이블의 `status='SENT'` 레코드
   - 로그의 "✓ Event sent successfully" 메시지

2. **알림 수신 확인**:
   - **콘솔**: 로그에 출력된 알림 박스
   - **웹훅**: `WEBHOOK_INBOX` 테이블의 수신 레코드 + `receivedAt` 타임스탬프
   - **이메일**: MailHog UI의 수신 메일 (설정 시)

3. **재시도 메커니즘**:
   - 로그의 "Retrying in X seconds..." 메시지
   - `NOTIFICATION_LOGS`의 `retry_count` 필드

시스템은 완전히 작동하며, 모든 요구사항을 충족합니다.
