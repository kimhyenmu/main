package com.example.assignment.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.example.assignment.model.ChatMessage;
import com.example.assignment.model.UserSession;
import com.example.assignment.service.ChatRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;

@Component
public class SocketIOHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SocketIOHandler.class);
    
    @Autowired
    private SocketIOServer socketIOServer;
    
    @Autowired
    private ChatRoomService chatRoomService;
    
    @PostConstruct
    public void startServer() {
        socketIOServer.addConnectListener(onConnected);
        socketIOServer.addDisconnectListener(onDisconnected);
        socketIOServer.addEventListener("join_room", Map.class, onJoinRoom);
        socketIOServer.addEventListener("leave_room", Map.class, onLeaveRoom);
        socketIOServer.addEventListener("send_message", Map.class, onSendMessage);
        socketIOServer.addEventListener("ping", String.class, onPing);
        
        socketIOServer.start();
        logger.info("Socket.IO 서버가 시작되었습니다.");
    }
    
    @PreDestroy
    public void stopServer() {
        socketIOServer.stop();
        logger.info("Socket.IO 서버가 중지되었습니다.");
    }
    
    private ConnectListener onConnected = new ConnectListener() {
        @Override
        public void onConnect(SocketIOClient client) {
            String sessionId = client.getSessionId().toString();
            String nickname = chatRoomService.generateAnonymousNickname();
            
            UserSession userSession = new UserSession(sessionId, nickname, client);
            chatRoomService.addUserSession(sessionId, userSession);
            
            client.sendEvent("connected", Map.of(
                "userId", sessionId,
                "nickname", nickname,
                "message", "연결되었습니다."
            ));
            
            logger.info("사용자 연결됨: {} ({})", nickname, sessionId);
        }
    };
    
    private DisconnectListener onDisconnected = new DisconnectListener() {
        @Override
        public void onDisconnect(SocketIOClient client) {
            String sessionId = client.getSessionId().toString();
            UserSession userSession = chatRoomService.getUserSession(sessionId);
            
            if (userSession != null && userSession.getCurrentRoomId() != null) {
                // 현재 방에서 퇴장 처리
                String roomId = userSession.getCurrentRoomId();
                chatRoomService.leaveRoom(sessionId, roomId);
                
                // 시스템 메시지 전송
                ChatMessage systemMessage = new ChatMessage(
                    ChatMessage.MessageType.SYSTEM,
                    roomId,
                    "시스템",
                    userSession.getNickname() + "님이 " + roomId + " 방을 나갔습니다."
                );
                
                chatRoomService.addMessage(roomId, systemMessage);
                socketIOServer.getRoomOperations(roomId).sendEvent("message", systemMessage);
                
                logger.info("사용자 퇴장: {} ({})", userSession.getNickname(), sessionId);
            }
            
            chatRoomService.removeUserSession(sessionId);
        }
    };
    
    private DataListener<Map> onJoinRoom = new DataListener<Map>() {
        @Override
        public void onData(SocketIOClient client, Map data, com.corundumstudio.socketio.AckRequest ackSender) {
            String sessionId = client.getSessionId().toString();
            String roomId = (String) data.get("roomId");
            
            UserSession userSession = chatRoomService.getUserSession(sessionId);
            if (userSession == null) {
                return;
            }
            
            // 이전 방에서 나가기
            if (userSession.getCurrentRoomId() != null) {
                client.leaveRoom(userSession.getCurrentRoomId());
                chatRoomService.leaveRoom(sessionId, userSession.getCurrentRoomId());
            }
            
            // 새 방에 입장
            client.joinRoom(roomId);
            chatRoomService.joinRoom(sessionId, roomId);
            
            // 방 히스토리 전송
            List<ChatMessage> history = chatRoomService.getRoomMessages(roomId);
            client.sendEvent("room_history", history);
            
            // 입장 시스템 메시지
            ChatMessage systemMessage = new ChatMessage(
                ChatMessage.MessageType.SYSTEM,
                roomId,
                "시스템",
                userSession.getNickname() + "님이 " + roomId + " 방에 입장했습니다."
            );
            
            chatRoomService.addMessage(roomId, systemMessage);
            socketIOServer.getRoomOperations(roomId).sendEvent("message", systemMessage);
            
            // 입장 확인 응답
            client.sendEvent("joined_room", Map.of(
                "roomId", roomId,
                "message", roomId + " 방에 입장했습니다."
            ));
            
            logger.info("사용자 방 입장: {} -> {}", userSession.getNickname(), roomId);
        }
    };
    
    private DataListener<Map> onLeaveRoom = new DataListener<Map>() {
        @Override
        public void onData(SocketIOClient client, Map data, com.corundumstudio.socketio.AckRequest ackSender) {
            String sessionId = client.getSessionId().toString();
            UserSession userSession = chatRoomService.getUserSession(sessionId);
            
            if (userSession != null && userSession.getCurrentRoomId() != null) {
                String roomId = userSession.getCurrentRoomId();
                
                client.leaveRoom(roomId);
                chatRoomService.leaveRoom(sessionId, roomId);
                
                // 퇴장 시스템 메시지
                ChatMessage systemMessage = new ChatMessage(
                    ChatMessage.MessageType.SYSTEM,
                    roomId,
                    "시스템",
                    userSession.getNickname() + "님이 " + roomId + " 방을 나갔습니다."
                );
                
                chatRoomService.addMessage(roomId, systemMessage);
                socketIOServer.getRoomOperations(roomId).sendEvent("message", systemMessage);
                
                client.sendEvent("left_room", Map.of(
                    "roomId", roomId,
                    "message", roomId + " 방에서 나갔습니다."
                ));
                
                logger.info("사용자 방 퇴장: {} <- {}", userSession.getNickname(), roomId);
            }
        }
    };
    
    private DataListener<Map> onSendMessage = new DataListener<Map>() {
        @Override
        public void onData(SocketIOClient client, Map data, com.corundumstudio.socketio.AckRequest ackSender) {
            String sessionId = client.getSessionId().toString();
            String text = (String) data.get("text");
            
            UserSession userSession = chatRoomService.getUserSession(sessionId);
            if (userSession == null || userSession.getCurrentRoomId() == null) {
                return;
            }
            
            String roomId = userSession.getCurrentRoomId();
            
            ChatMessage chatMessage = new ChatMessage(
                ChatMessage.MessageType.CHAT,
                roomId,
                userSession.getNickname(),
                text
            );
            
            chatRoomService.addMessage(roomId, chatMessage);
            socketIOServer.getRoomOperations(roomId).sendEvent("message", chatMessage);
            
            logger.info("메시지 전송: {} -> {}: {}", userSession.getNickname(), roomId, text);
        }
    };
    
    private DataListener<String> onPing = new DataListener<String>() {
        @Override
        public void onData(SocketIOClient client, String data, com.corundumstudio.socketio.AckRequest ackSender) {
            client.sendEvent("pong", "pong");
        }
    };
}