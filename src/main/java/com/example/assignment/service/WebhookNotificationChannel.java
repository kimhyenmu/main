package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.NotificationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebhookNotificationChannel implements NotificationChannel {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${monitoring.notification.webhook.url:}")
    private String webhookUrl;
    
    @Value("${monitoring.notification.webhook.enabled:true}")
    private boolean enabled;
    
    public WebhookNotificationChannel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    @Override
    public NotificationLog send(Alert alert) {
        LocalDateTime now = LocalDateTime.now();
        
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook notification is disabled or URL is not configured");
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.WEBHOOK)
                    .status(NotificationLog.NotificationStatus.FAILED)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(0)
                    .resultMessage("Webhook is disabled or not configured")
                    .build();
        }
        
        try {
            // 웹훅 페이로드 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("alert_id", alert.getId());
            payload.put("target_url", alert.getTargetUrl());
            payload.put("status", alert.getStatus().name());
            payload.put("status_code", alert.getStatusCode());
            payload.put("response_time_ms", alert.getResponseTimeMs());
            payload.put("message", alert.getMessage());
            payload.put("detected_at", alert.getDetectedAt().toString());
            payload.put("timestamp", now.toString());
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "URL-Monitor/1.0")
                    .build();
            
            log.info("Sending webhook notification to: {}", webhookUrl);
            log.debug("Payload: {}", jsonPayload);
            
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                
                log.info("Webhook response: {} - {}", statusCode, responseBody);
                
                if (response.isSuccessful()) {
                    return NotificationLog.builder()
                            .alert(alert)
                            .channel(NotificationLog.NotificationChannel.WEBHOOK)
                            .status(NotificationLog.NotificationStatus.SENT)
                            .attemptedAt(now)
                            .retryCount(0)
                            .resultCode(statusCode)
                            .resultMessage("Webhook sent successfully: " + responseBody)
                            .messageId("WEBHOOK-" + alert.getId() + "-" + now.toString())
                            .build();
                } else {
                    return NotificationLog.builder()
                            .alert(alert)
                            .channel(NotificationLog.NotificationChannel.WEBHOOK)
                            .status(NotificationLog.NotificationStatus.FAILED)
                            .attemptedAt(now)
                            .retryCount(0)
                            .resultCode(statusCode)
                            .resultMessage("Webhook failed with status: " + statusCode + " - " + responseBody)
                            .build();
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send webhook notification", e);
            
            return NotificationLog.builder()
                    .alert(alert)
                    .channel(NotificationLog.NotificationChannel.WEBHOOK)
                    .status(NotificationLog.NotificationStatus.FAILED)
                    .attemptedAt(now)
                    .retryCount(0)
                    .resultCode(0)
                    .resultMessage("Exception: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public NotificationLog.NotificationChannel getChannelType() {
        return NotificationLog.NotificationChannel.WEBHOOK;
    }
}
