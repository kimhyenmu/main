package com.example.assignment.controller;

import com.example.assignment.dto.ApiResponse;
import com.example.assignment.dto.HealthCheckEventDto;
import com.example.assignment.service.EventProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    
    private final EventProcessingService eventProcessingService;
    
    @Value("${monitoring.api.key}")
    private String expectedApiKey;
    
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> receiveEvent(
            @Valid @RequestBody HealthCheckEventDto eventDto) {
        
        log.info("Received health check event for URL: {}", eventDto.getTargetUrl());
        
        // API 키 검증
        if (!expectedApiKey.equals(eventDto.getApiKey())) {
            log.warn("Invalid API key provided");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid API key"));
        }
        
        try {
            Long healthCheckId = eventProcessingService.processHealthCheckEvent(eventDto);
            log.info("Successfully processed health check event. ID: {}", healthCheckId);
            
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Event processed successfully", healthCheckId));
                    
        } catch (Exception e) {
            log.error("Error processing health check event", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process event: " + e.getMessage()));
        }
    }
}
