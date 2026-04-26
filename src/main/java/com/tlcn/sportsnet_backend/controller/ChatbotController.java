package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.chatbot.*;
import com.tlcn.sportsnet_backend.entity.ChatMessage;
import com.tlcn.sportsnet_backend.entity.ChatSession;
import com.tlcn.sportsnet_backend.repository.ChatMessageRepository;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.ChatSessionRepository;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatMessageRepository chatMessageRepository;
    private final AccountRepository accountRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chatbot.url:http://127.0.0.1:9000/chat}")
    private String chatbotUrl;

    @PostMapping("/ask")
    public ResponseEntity<?> askChatbot(
            @RequestBody ChatbotRequest request,
            HttpServletRequest httpRequest
    ) {
        // Lấy email hiện tại từ security context
        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        
        var account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : UUID.randomUUID().toString();
        request.setSessionId(sessionId);

        // Lấy hoặc tạo ChatSession
        ChatSession chatSession = chatSessionRepository.findByAccountAndSessionId(account, sessionId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder()
                        .account(account)
                        .sessionId(sessionId)
                        .build()));
        chatSession.setUpdatedAt(java.time.Instant.now());
        chatSessionRepository.save(chatSession);

        // ⭐ Lấy Authorization header từ FE
        String authHeader = httpRequest.getHeader("Authorization");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<ChatbotRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<?> response = restTemplate.exchange(
                chatbotUrl,
                HttpMethod.POST,
                entity,
                Object.class
        );
        String assistantContent = extractAssistantContent(response.getBody());
        chatMessageRepository.save(ChatMessage.builder()
                .account(account)
                .chatSession(chatSession)
                .sessionId(sessionId)
                .role("user")
                .content(request.getQuestion())
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .account(account)
                .chatSession(chatSession)
                .sessionId(sessionId)
                .role("assistant")
                .content(assistantContent)
                .build());
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("answer", assistantContent);
        return ResponseEntity.ok(result);
    }

    private String extractAssistantContent(Object responseBody) {
        if (responseBody instanceof Map<?, ?> map) {
            Object answer = map.get("answer");
            if (answer == null) answer = map.get("response");
            if (answer == null) answer = map.get("message");
            if (answer != null) {
                return String.valueOf(answer);
            }
        }

        return responseBody == null ? "" : String.valueOf(responseBody);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        var account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        Pageable pageable = PageRequest.of(page, size);
        List<ChatSession> chatSessions = chatSessionRepository.findByAccountOrderByUpdatedAtDesc(account);
        int start = Math.min(page * size, chatSessions.size());
        int end = Math.min(start + size, chatSessions.size());
        List<ChatSession> pagedSessions = chatSessions.subList(start, end);
        List<ChatSessionResponse> sessions = pagedSessions.stream().map(session -> {
            return chatMessageRepository.findFirstByAccountIdAndSessionIdAndRoleOrderByCreatedAtAsc(account.getId(), session.getSessionId(), "user")
                    .map(firstUserMessage -> ChatSessionResponse.builder()
                            .sessionId(session.getSessionId())
                            .lastMessageTime(firstUserMessage.getCreatedAt())
                            .lastMessage(firstUserMessage.getContent())
                            .lastRole(firstUserMessage.getRole())
                            .updatedAt(session.getUpdatedAt())
                            .build())
                    .orElse(null);
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
        PagedResponse<ChatSessionResponse> response = new PagedResponse<>(
                sessions,
                page,
                size,
                chatSessions.size(),
                (chatSessions.size() + size - 1) / size,
                end == chatSessions.size()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSessionDetails(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        
        var account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!chatMessageRepository.existsByAccountIdAndSessionId(account.getId(), sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found or empty"));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatMessageRepository
                .findByAccountIdAndSessionIdOrderByCreatedAtAsc(account.getId(), sessionId, pageable);

        List<ChatMessageResponse> messageResponses = messages.getContent().stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getId())
                        .sessionId(m.getSessionId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        ChatSessionDetailResponse response = ChatSessionDetailResponse.builder()
                .sessionId(sessionId)
                .messages(messageResponses)
                .page(messages.getNumber())
                .size(messages.getSize())
                .totalElements(messages.getTotalElements())
                .totalPages(messages.getTotalPages())
                .last(messages.isLast())
                .build();

        return ResponseEntity.ok(response);
    }
}
