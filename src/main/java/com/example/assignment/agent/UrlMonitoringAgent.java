package com.example.assignment.agent;

import com.example.assignment.dto.HealthCheckEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UrlMonitoringAgent {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${monitoring.agent.target-url}")
    private String targetUrl;
    
    @Value("${monitoring.agent.server-url}")
    private String serverUrl;
    
    @Value("${monitoring.agent.api-key}")
    private String apiKey;
    
    @Value("${monitoring.agent.enabled:true}")
    private boolean enabled;
    
    @Value("${monitoring.agent.max-retries:3}")
    private int maxRetries;
    
    public UrlMonitoringAgent(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 주기적으로 대상 URL을 점검합니다
     * cron: 초 분 시 일 월 요일
     * fixedDelayString: 이전 작업 완료 후 지연 시간 (밀리초)
     */
    @Scheduled(fixedDelayString = "${monitoring.agent.check-interval:30000}")
    public void monitorUrl() {
        if (!enabled) {
            log.debug("Monitoring agent is disabled");
            return;
        }
        
        if (targetUrl == null || targetUrl.isEmpty()) {
            log.warn("Target URL is not configured");
            return;
        }
        
        log.info("=== Starting URL health check for: {} ===", targetUrl);
        
        // 1. 대상 URL 점검
        HealthCheckEventDto event = performHealthCheck(targetUrl);
        
        // 2. 백엔드로 전송 (재시도 포함)
        sendEventToServer(event);
        
        log.info("=== Health check completed ===\n");
    }
    
    /**
     * 대상 URL을 점검하고 결과를 수집합니다
     */
    private HealthCheckEventDto performHealthCheck(String url) {
        LocalDateTime timestamp = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        
        int statusCode = 0;
        String errorMessage = null;
        
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            log.info("Sending request to: {}", url);
            
            try (Response response = httpClient.newCall(request).execute()) {
                statusCode = response.code();
                long responseTime = System.currentTimeMillis() - startTime;
                
                log.info("Response received - Status: {}, Time: {} ms", statusCode, responseTime);
                
                return HealthCheckEventDto.builder()
                        .targetUrl(url)
                        .statusCode(statusCode)
                        .responseTimeMs(responseTime)
                        .timestamp(timestamp)
                        .apiKey(apiKey)
                        .build();
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            errorMessage = e.getMessage();
            
            log.error("Health check failed for URL: {} - Error: {}", url, errorMessage);
            
            // 에러 발생 시에도 이벤트를 전송 (상태 코드 0 또는 -1로 표시)
            return HealthCheckEventDto.builder()
                    .targetUrl(url)
                    .statusCode(statusCode > 0 ? statusCode : -1)
                    .responseTimeMs(responseTime)
                    .timestamp(timestamp)
                    .errorMessage(errorMessage)
                    .apiKey(apiKey)
                    .build();
        }
    }
    
    /**
     * 수집 결과를 백엔드 서버로 전송합니다 (재시도 포함)
     */
    private void sendEventToServer(HealthCheckEventDto event) {
        String eventsEndpoint = serverUrl + "/events";
        int attempt = 0;
        
        while (attempt < maxRetries) {
            attempt++;
            
            try {
                String jsonPayload = objectMapper.writeValueAsString(event);
                
                RequestBody body = RequestBody.create(
                        jsonPayload,
                        MediaType.parse("application/json")
                );
                
                Request request = new Request.Builder()
                        .url(eventsEndpoint)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                
                log.info("[Attempt {}/{}] Sending event to server: {}", attempt, maxRetries, eventsEndpoint);
                log.debug("Payload: {}", jsonPayload);
                
                try (Response response = httpClient.newCall(request).execute()) {
                    int statusCode = response.code();
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (response.isSuccessful()) {
                        log.info("✓ Event sent successfully - Status: {}, Response: {}", statusCode, responseBody);
                        return; // 성공 시 종료
                    } else {
                        log.warn("✗ Event send failed - Status: {}, Response: {}", statusCode, responseBody);
                        
                        if (attempt < maxRetries) {
                            int delaySeconds = attempt * 2; // 지수 백오프
                            log.info("Retrying in {} seconds...", delaySeconds);
                            Thread.sleep(delaySeconds * 1000L);
                        }
                    }
                }
                
            } catch (Exception e) {
                log.error("✗ Failed to send event (Attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        int delaySeconds = attempt * 2;
                        log.info("Retrying in {} seconds...", delaySeconds);
                        Thread.sleep(delaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted");
                        break;
                    }
                }
            }
        }
        
        log.error("✗ Failed to send event after {} attempts", maxRetries);
    }
}
