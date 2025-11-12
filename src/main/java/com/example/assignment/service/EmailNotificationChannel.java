package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {
    
    private final JavaMailSender mailSender;
    
    @Value("${monitoring.notification.email.to:}")
    private String emailTo;
    
    @Value("${monitoring.notification.email.from:noreply@monitoring.local}")
    private String emailFrom;
    
    @Value("${monitoring.notification.email.enabled:false}")
    private boolean enabled;
    
    @Override
    public NotificationLog send(Alert alert) {
        LocalDateTime now = LocalDateTime.now();
        
        if (!enabled || emailTo == null || emailTo.isEmpty()) {
            log.warn("Email notification is disabled or recipient is not configured");
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.EMAIL)
                    .status(NotificationLog.NotificationStatus.FAILED)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(0)
                    .resultMessage("Email is disabled or not configured")
                    .build();
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(emailTo);
            message.setSubject(String.format(
                    "[ALERT] %s - %s",
                    alert.getStatus(),
                    alert.getTargetUrl()
            ));
            
            String body = String.format(
                    "Alert Notification\n" +
                    "==================\n\n" +
                    "Alert ID: %d\n" +
                    "Target URL: %s\n" +
                    "Status: %s\n" +
                    "HTTP Status Code: %d\n" +
                    "Response Time: %d ms\n" +
                    "Detected At: %s\n\n" +
                    "Message:\n%s\n\n" +
                    "---\n" +
                    "This is an automated notification from URL Monitoring System.",
                    alert.getId(),
                    alert.getTargetUrl(),
                    alert.getStatus(),
                    alert.getStatusCode(),
                    alert.getResponseTimeMs(),
                    alert.getDetectedAt(),
                    alert.getMessage()
            );
            
            message.setText(body);
            
            log.info("Sending email notification to: {}", emailTo);
            mailSender.send(message);
            log.info("Email sent successfully");
            
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.EMAIL)
                    .status(NotificationLog.NotificationStatus.SENT)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(200)
                    .resultMessage("Email sent successfully to: " + emailTo)
                    .messageId("EMAIL-" + alert.getId() + "-" + now.toString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to send email notification", e);
            
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.EMAIL)
                    .status(NotificationLog.NotificationStatus.FAILED)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(500)
                    .resultMessage("Email failed: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public NotificationLog.NotificationChannel getChannelType() {
        return NotificationLog.NotificationChannel.EMAIL;
    }
}
