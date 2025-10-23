package com.example.assignment.service;

import com.example.assignment.model.ChatMessage;
import com.example.assignment.model.UserSession;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatRoomService {
    
    // 방별 메시지 히스토리 (최근 50개)
    private final Map<String, List<ChatMessage>> roomMessages = new ConcurrentHashMap<>();
    
    // 방별 사용자 목록
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    
    // 사용자 세션 관리
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();
    
    // 사용자가 방에 입장
    public void joinRoom(String userId, String roomId) {
        // 방이 없으면 생성
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        roomMessages.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
        
        // 사용자 세션 업데이트
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setCurrentRoomId(roomId);
        }
    }
    
    // 사용자가 방에서 퇴장
    public void leaveRoom(String userId, String roomId) {
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(userId);
            
            // 방이 비어있으면 삭제
            if (users.isEmpty()) {
                roomUsers.remove(roomId);
                roomMessages.remove(roomId);
            }
        }
        
        // 사용자 세션 업데이트
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setCurrentRoomId(null);
        }
    }
    
    // 메시지 추가
    public void addMessage(String roomId, ChatMessage message) {
        List<ChatMessage> messages = roomMessages.get(roomId);
        if (messages != null) {
            synchronized (messages) {
                messages.add(message);
                
                // 최근 50개만 유지
                if (messages.size() > 50) {
                    messages.remove(0);
                }
            }
        }
    }
    
    // 방의 메시지 히스토리 조회
    public List<ChatMessage> getRoomMessages(String roomId) {
        List<ChatMessage> messages = roomMessages.get(roomId);
        if (messages != null) {
            synchronized (messages) {
                return new ArrayList<>(messages);
            }
        }
        return new ArrayList<>();
    }
    
    // 방의 사용자 목록 조회
    public Set<String> getRoomUsers(String roomId) {
        Set<String> users = roomUsers.get(roomId);
        return users != null ? new HashSet<>(users) : new HashSet<>();
    }
    
    // 사용자 세션 추가
    public void addUserSession(String userId, UserSession session) {
        userSessions.put(userId, session);
    }
    
    // 사용자 세션 제거
    public void removeUserSession(String userId) {
        userSessions.remove(userId);
    }
    
    // 사용자 세션 조회
    public UserSession getUserSession(String userId) {
        return userSessions.get(userId);
    }
    
    // 모든 방 목록 조회
    public Set<String> getAllRooms() {
        return new HashSet<>(roomUsers.keySet());
    }
    
    // 익명 닉네임 생성
    public String generateAnonymousNickname() {
        return "user-" + String.format("%04d", new Random().nextInt(10000));
    }
}