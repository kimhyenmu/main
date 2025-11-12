package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.NotificationLog;
import com.example.assignment.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final List<NotificationChannel> notificationChannels;
    private final NotificationLogRepository notificationLogRepository;
    
    /**
     * 모든 활성화된 채널로 알림을 전송합니다
     */
    @Transactional
    public List<NotificationLog> sendNotifications(Alert alert) {
        log.info("Sending notifications for alert ID: {}", alert.getId());
        
        List<NotificationLog> logs = new ArrayList<>();
        
        for (NotificationChannel channel : notificationChannels) {
            try {
                log.info("Attempting to send notification via: {}", channel.getChannelType());
                
                // 각 채널로 알림 전송
                NotificationLog notificationLog = channel.send(alert);
                
                // 로그 저장
                NotificationLog savedLog = notificationLogRepository.save(notificationLog);
                logs.add(savedLog);
                
                log.info("Notification via {} - Status: {}, Result: {}",
                        channel.getChannelType(),
                        notificationLog.getStatus(),
                        notificationLog.getResultMessage());
                        
            } catch (Exception e) {
                log.error("Failed to send notification via {}", channel.getChannelType(), e);
                
                // 실패 로그 저장
                NotificationLog errorLog = NotificationLog.builder()
                        .alert(alert)
                        .channel(channel.getChannelType())
                        .status(NotificationLog.NotificationStatus.FAILED)
                        .attemptedAt(java.time.LocalDateTime.now())
                        .retryCount(0)
                        .resultCode(500)
                        .resultMessage("Exception: " + e.getMessage())
                        .build();
                
                NotificationLog savedLog = notificationLogRepository.save(errorLog);
                logs.add(savedLog);
            }
        }
        
        return logs;
    }
    
    /**
     * 특정 채널로만 알림을 재전송합니다
     */
    @Transactional
    public NotificationLog retryNotification(Alert alert, NotificationLog.NotificationChannel channelType) {
        log.info("Retrying notification for alert ID: {} via {}", alert.getId(), channelType);
        
        NotificationChannel channel = notificationChannels.stream()
                .filter(c -> c.getChannelType() == channelType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelType));
        
        NotificationLog notificationLog = channel.send(alert);
        return notificationLogRepository.save(notificationLog);
    }
}
