package com.example.assignment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "alerts")
public class Alert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String targetUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;
    
    @Column(nullable = false)
    private Integer statusCode;
    
    @Column(nullable = false)
    private Long responseTimeMs;
    
    @Column(length = 2000)
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime detectedAt;
    
    private LocalDateTime acknowledgedAt;
    
    private LocalDateTime resolvedAt;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum AlertStatus {
        OPEN,       // 새로 발생한 알림
        ACK,        // 확인됨
        RESOLVED    // 해결됨
    }
}
