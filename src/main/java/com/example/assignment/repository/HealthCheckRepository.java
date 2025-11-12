package com.example.assignment.repository;

import com.example.assignment.entity.HealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {
    
    List<HealthCheck> findByTargetUrlAndTimestampBetween(
            String targetUrl, 
            LocalDateTime start, 
            LocalDateTime end
    );
    
    HealthCheck findTopByTargetUrlOrderByTimestampDesc(String targetUrl);
}
