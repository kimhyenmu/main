package com.example.assignment.controller;

import com.example.assignment.dto.ApiResponse;
import com.example.assignment.entity.WebhookInbox;
import com.example.assignment.repository.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/_test/inbox")
@RequiredArgsConstructor
public class WebhookInboxController {
    
    private final WebhookInboxRepository webhookInboxRepository;
    
    /**
     * 테스트용 웹훅 수신 엔드포인트 - 모든 HTTP 메서드 수용
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> receiveWebhook(
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers) {
        
        log.info("=== WEBHOOK RECEIVED ===");
        log.info("Method: POST");
        log.info("Headers: {}", headers);
        log.info("Body: {}", body);
        log.info("========================");
        
        // 수신 데이터 저장
        WebhookInbox inbox = WebhookInbox.builder()
                .payload(body != null ? body : "")
                .method("POST")
                .headers(headers.toString())
                .receivedAt(LocalDateTime.now())
                .build();
        
        webhookInboxRepository.save(inbox);
        
        return ResponseEntity.ok(
                ApiResponse.success("Webhook received and stored", "OK")
        );
    }
    
    /**
     * 수신된 웹훅 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WebhookInbox>>> getReceivedWebhooks() {
        List<WebhookInbox> webhooks = webhookInboxRepository.findTop10ByOrderByReceivedAtDesc();
        
        log.info("Retrieved {} webhook records", webhooks.size());
        
        return ResponseEntity.ok(
                ApiResponse.success("Retrieved webhook inbox", webhooks)
        );
    }
}
