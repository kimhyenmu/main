package com.example.assignment.service;

import com.example.assignment.dto.HealthCheckEventDto;
import com.example.assignment.entity.HealthCheck;
import com.example.assignment.repository.HealthCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessingService {
    
    private final HealthCheckRepository healthCheckRepository;
    private final AlertService alertService;
    
    /**
     * 헬스체크 이벤트를 처리합니다
     */
    @Transactional
    public Long processHealthCheckEvent(HealthCheckEventDto eventDto) {
        log.info("Processing health check event for URL: {}", eventDto.getTargetUrl());
        
        // 1. 헬스체크 데이터 저장
        HealthCheck healthCheck = HealthCheck.builder()
                .targetUrl(eventDto.getTargetUrl())
                .statusCode(eventDto.getStatusCode())
                .responseTimeMs(eventDto.getResponseTimeMs())
                .timestamp(eventDto.getTimestamp())
                .errorMessage(eventDto.getErrorMessage())
                .build();
        
        HealthCheck saved = healthCheckRepository.save(healthCheck);
        log.info("Health check saved with ID: {}", saved.getId());
        
        // 2. 알림 감지 및 처리
        try {
            alertService.detectAndProcessAlert(saved);
        } catch (Exception e) {
            log.error("Error while detecting/processing alert", e);
            // 알림 처리 실패해도 헬스체크 데이터는 저장됨
        }
        
        return saved.getId();
    }
}
