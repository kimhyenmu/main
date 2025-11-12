package com.example.assignment.repository;

import com.example.assignment.entity.WebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, Long> {
    
    List<WebhookInbox> findByReceivedAtAfter(LocalDateTime after);
    
    List<WebhookInbox> findTop10ByOrderByReceivedAtDesc();
}
