package com.example.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckEventDto {
    
    @NotBlank(message = "Target URL is required")
    private String targetUrl;
    
    @NotNull(message = "Status code is required")
    private Integer statusCode;
    
    @NotNull(message = "Response time is required")
    private Long responseTimeMs;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    private String errorMessage;
    
    @NotBlank(message = "API key is required")
    private String apiKey;
}
