package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.NotificationLog;

public interface NotificationChannel {
    
    /**
     * 알림을 전송합니다
     * @param alert 알림 엔티티
     * @return 전송 결과 로그
     */
    NotificationLog send(Alert alert);
    
    /**
     * 채널 타입을 반환합니다
     */
    NotificationLog.NotificationChannel getChannelType();
}
