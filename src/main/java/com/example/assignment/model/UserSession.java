package com.example.assignment.model;

import com.corundumstudio.socketio.SocketIOClient;

public class UserSession {
    private String userId;
    private String nickname;
    private String currentRoomId;
    private SocketIOClient client;
    
    public UserSession(String userId, String nickname, SocketIOClient client) {
        this.userId = userId;
        this.nickname = nickname;
        this.client = client;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getCurrentRoomId() {
        return currentRoomId;
    }
    
    public void setCurrentRoomId(String currentRoomId) {
        this.currentRoomId = currentRoomId;
    }
    
    public SocketIOClient getClient() {
        return client;
    }
    
    public void setClient(SocketIOClient client) {
        this.client = client;
    }
}