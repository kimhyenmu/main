package com.example.assignment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class ChatMessage {
    public enum MessageType {
        CHAT, SYSTEM
    }
    
    private MessageType type;
    private String roomId;
    private String sender;
    private String text;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;
    
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ChatMessage(MessageType type, String roomId, String sender, String text) {
        this.type = type;
        this.roomId = roomId;
        this.sender = sender;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "type=" + type +
                ", roomId='" + roomId + '\'' +
                ", sender='" + sender + '\'' +
                ", text='" + text + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}