package com.example.assignment.repository;

import com.example.assignment.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    Optional<Alert> findTopByTargetUrlAndStatusOrderByDetectedAtDesc(
            String targetUrl, 
            Alert.AlertStatus status
    );
    
    List<Alert> findByTargetUrlAndStatus(String targetUrl, Alert.AlertStatus status);
    
    List<Alert> findByStatus(Alert.AlertStatus status);
}
