# 实时聊天应用程序

基于Socket.IO的实时多房间聊天应用程序，支持Room功能。

## 功能特性

### 服务器端
- ✅ Socket.IO 기반 WebSocket 서버
- ✅ 클라이언트 접속 시 익명 닉네임(user-xxxx) 자동 부여
- ✅ Room 단위의 채팅 지원
  - 특정 roomId로 입장(join) 요청
  - 방이 없으면 자동 생성
  - 퇴장 시 leave 처리 및 마지막 사용자 퇴장 시 방 삭제
- ✅ 메시지 처리
  - 클라이언트가 보낸 메시지는 해당 roomId 내 모든 사용자에게 브로드캐스트
  - 시스템 메시지(type=system)로 입장/퇴장 안내
- ✅ 방별 최근 50개 메시지 메모리 저장, 새 사용자 입장 시 전달
- ✅ ping/pong 하트비트로 연결 유지 관리

### 클라이언트端
- ✅ 서버 연결 및 상태 표시(연결됨/끊김)
- ✅ Room 선택 또는 생성 후 입장
- ✅ 메시지 입력창과 전송 버튼(Enter 전송, Shift+Enter 줄바꿈)
- ✅ 내 메시지: 오른쪽 정렬 / 타인 메시지: 왼쪽 정렬 / 시스템 메시지: 회색
- ✅ 연결이 끊기면 자동 재연결 및 마지막 roomId로 재입장
- ✅ 탭 종료 시 서버에 leave 신호 전송

## 메시지 형식

```json
{
  "type": "CHAT" | "SYSTEM",
  "roomId": "string",
  "sender": "string", 
  "text": "string",
  "timestamp": "ISO8601 string"
}
```

## 실행 방법

### 1. 애플리케이션 빌드 및 실행

```bash
# 권한 부여
chmod +x gradlew

# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

### 2. 웹 브라우저에서 접속

```
http://localhost:8080
```

## 포트 설정

- **웹 서버**: 8080 포트
- **Socket.IO 서버**: 9092 포트

## 사용 방법

1. 웹 브라우저에서 `http://localhost:8080` 접속
2. 자동으로 익명 닉네임이 부여됨 (user-xxxx 형식)
3. 방 이름을 입력하고 "입장" 버튼 클릭
4. 메시지를 입력하고 Enter 키로 전송
5. 다른 브라우저 탭에서 같은 방에 입장하여 채팅 테스트

## 예시 흐름

1. 클라이언트가 roomA로 join 요청
2. 서버가 roomA 생성 후 user-1234 입장 처리
3. 다른 사용자가 roomA로 join 시 기존 히스토리 50개 수신
4. 메시지 송신 시 roomA 사용자에게만 broadcast
5. 퇴장 시 system 메시지 "user-1234님이 roomA 방을 나갔습니다" 전송

## 기술 스택

- **백엔드**: Spring Boot 3.5.5 + Java 21
- **WebSocket**: netty-socketio 2.0.3
- **프론트엔드**: HTML5 + CSS3 + JavaScript (Vanilla)
- **빌드 도구**: Gradle 8.14.3

## 주요 클래스

- `SocketIOConfig`: Socket.IO 서버 설정
- `ChatMessage`: 채팅 메시지 모델
- `UserSession`: 사용자 세션 관리
- `ChatRoomService`: 채팅방 및 메시지 관리 서비스
- `SocketIOHandler`: WebSocket 이벤트 처리
- `ChatController`: 웹 페이지 컨트롤러