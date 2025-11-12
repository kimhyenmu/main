package com.example.assignment.repository;

import com.example.assignment.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    
    List<NotificationLog> findByAlertId(Long alertId);
    
    List<NotificationLog> findByStatus(NotificationLog.NotificationStatus status);
}
