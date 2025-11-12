package com.example.assignment.service;

import com.example.assignment.entity.Alert;
import com.example.assignment.entity.HealthCheck;
import com.example.assignment.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {
    
    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    
    @Value("${monitoring.alert.status-code-threshold:400}")
    private int statusCodeThreshold;
    
    @Value("${monitoring.alert.response-time-threshold:5000}")
    private long responseTimeThreshold;
    
    /**
     * 헬스체크 결과를 기반으로 알림을 감지하고 처리합니다
     */
    @Transactional
    public void detectAndProcessAlert(HealthCheck healthCheck) {
        String targetUrl = healthCheck.getTargetUrl();
        
        // 비정상 상태 확인
        boolean isAbnormal = isAbnormalResponse(healthCheck);
        
        if (isAbnormal) {
            handleAbnormalResponse(healthCheck);
        } else {
            handleNormalResponse(healthCheck);
        }
    }
    
    /**
     * 비정상 응답인지 확인
     */
    private boolean isAbnormalResponse(HealthCheck healthCheck) {
        // 상태 코드가 임계값 이상이거나 응답 시간이 임계값 이상인 경우
        return healthCheck.getStatusCode() >= statusCodeThreshold ||
               healthCheck.getResponseTimeMs() >= responseTimeThreshold;
    }
    
    /**
     * 비정상 응답 처리
     */
    private void handleAbnormalResponse(HealthCheck healthCheck) {
        String targetUrl = healthCheck.getTargetUrl();
        
        // 이미 열려있는 알림이 있는지 확인 (중복 알림 방지)
        Optional<Alert> existingAlert = alertRepository.findTopByTargetUrlAndStatusOrderByDetectedAtDesc(
                targetUrl,
                Alert.AlertStatus.OPEN
        );
        
        if (existingAlert.isPresent()) {
            log.info("Alert already exists for URL: {}. Skipping duplicate alert.", targetUrl);
            return;
        }
        
        // 새 알림 생성
        Alert alert = Alert.builder()
                .targetUrl(targetUrl)
                .status(Alert.AlertStatus.OPEN)
                .statusCode(healthCheck.getStatusCode())
                .responseTimeMs(healthCheck.getResponseTimeMs())
                .message(buildAlertMessage(healthCheck))
                .detectedAt(healthCheck.getTimestamp())
                .build();
        
        Alert savedAlert = alertRepository.save(alert);
        
        log.warn("New alert created for URL: {} - Alert ID: {}", targetUrl, savedAlert.getId());
        
        // 알림 전송
        notificationService.sendNotifications(savedAlert);
    }
    
    /**
     * 정상 응답 처리 (이전 알림 해결)
     */
    private void handleNormalResponse(HealthCheck healthCheck) {
        String targetUrl = healthCheck.getTargetUrl();
        
        // 열려있는 알림 찾기
        Optional<Alert> openAlert = alertRepository.findTopByTargetUrlAndStatusOrderByDetectedAtDesc(
                targetUrl,
                Alert.AlertStatus.OPEN
        );
        
        if (openAlert.isPresent()) {
            Alert alert = openAlert.get();
            alert.setStatus(Alert.AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            
            alertRepository.save(alert);
            
            log.info("Alert resolved for URL: {} - Alert ID: {}", targetUrl, alert.getId());
            
            // 해결 알림 전송
            notificationService.sendNotifications(alert);
        }
    }
    
    /**
     * 알림 메시지 생성
     */
    private String buildAlertMessage(HealthCheck healthCheck) {
        StringBuilder message = new StringBuilder();
        message.append("URL monitoring alert detected. ");
        
        if (healthCheck.getStatusCode() >= statusCodeThreshold) {
            message.append(String.format("Status code %d is above threshold %d. ",
                    healthCheck.getStatusCode(), statusCodeThreshold));
        }
        
        if (healthCheck.getResponseTimeMs() >= responseTimeThreshold) {
            message.append(String.format("Response time %d ms is above threshold %d ms. ",
                    healthCheck.getResponseTimeMs(), responseTimeThreshold));
        }
        
        if (healthCheck.getErrorMessage() != null) {
            message.append("Error: ").append(healthCheck.getErrorMessage());
        }
        
        return message.toString();
    }
    
    /**
     * 알림 확인 처리
     */
    @Transactional
    public Alert acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
        
        if (alert.getStatus() == Alert.AlertStatus.OPEN) {
            alert.setStatus(Alert.AlertStatus.ACK);
            alert.setAcknowledgedAt(LocalDateTime.now());
            
            return alertRepository.save(alert);
        }
        
        return alert;
    }
}
