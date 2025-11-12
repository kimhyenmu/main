package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.NotificationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class ConsoleNotificationChannel implements NotificationChannel {
    
    @Override
    public NotificationLog send(Alert alert) {
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // 콘솔에 알림 출력
            String notification = String.format(
                    "\n" +
                    "╔════════════════════════════════════════════════════════════╗\n" +
                    "║              🚨 ALERT NOTIFICATION                        ║\n" +
                    "╠════════════════════════════════════════════════════════════╣\n" +
                    "║ Alert ID    : %-44d║\n" +
                    "║ Target URL  : %-44s║\n" +
                    "║ Status      : %-44s║\n" +
                    "║ Status Code : %-44d║\n" +
                    "║ Response    : %-41d ms║\n" +
                    "║ Detected At : %-44s║\n" +
                    "║ Message     : %-44s║\n" +
                    "╚════════════════════════════════════════════════════════════╝\n",
                    alert.getId(),
                    truncate(alert.getTargetUrl(), 44),
                    alert.getStatus(),
                    alert.getStatusCode(),
                    alert.getResponseTimeMs(),
                    alert.getDetectedAt().toString(),
                    truncate(alert.getMessage(), 44)
            );
            
            log.info(notification);
            System.out.println(notification);
            System.out.flush();
            
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.CONSOLE)
                    .status(NotificationLog.NotificationStatus.SENT)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(200)
                    .resultMessage("Successfully printed to console")
                    .messageId("CONSOLE-" + now.toString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send console notification", e);
            
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.CONSOLE)
                    .status(NotificationLog.NotificationStatus.FAILED)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(500)
                    .resultMessage("Console notification failed: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public NotificationLog.NotificationChannel getChannelType() {
        return NotificationLog.NotificationChannel.CONSOLE;
    }
    
    private String truncate(String str, int length) {
        if (str == null) return "";
        return str.length() <= length ? str : str.substring(0, length - 3) + "...";
    }
}
