package com.tlcn.sportsnet_backend.controller;

import com.tlcn.sportsnet_backend.dto.chatbot.ChatMessageResponse;
import com.tlcn.sportsnet_backend.dto.chatbot.ChatSessionDetailResponse;
import com.tlcn.sportsnet_backend.dto.chatbot.ChatSessionResponse;
import com.tlcn.sportsnet_backend.dto.chatbot.ChatbotRequest;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.ChatMessage;
import com.tlcn.sportsnet_backend.entity.ChatSession;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.ChatMessageRepository;
import com.tlcn.sportsnet_backend.repository.ChatSessionRepository;
import com.tlcn.sportsnet_backend.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private static final String DEFAULT_TITLE = "Cuoc tro chuyen moi";
    private static final String DEFAULT_CHATBOT_URL = "http://127.0.0.1:9000/chat";
    private static final String DEFAULT_CHATBOT_TITLE_URL = "http://127.0.0.1:9000/conversation-title";
    private static final int MAX_TITLE_LENGTH = 120;

    private final ChatMessageRepository chatMessageRepository;
    private final AccountRepository accountRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chatbot.url:http://127.0.0.1:9000/chat}")
    private String configuredChatbotUrl;

    @Value("${chatbot.set-name:http://127.0.0.1:9000/conversation-title}")
    private String configuredChatSetNameUrl;

    @PostMapping("/ask")
    public ResponseEntity<?> askChatbot(
            @RequestBody ChatbotRequest request,
            HttpServletRequest httpRequest
    ) {
        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        Account account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        String sessionId = StringUtils.hasText(request.getSessionId()) 
                ? request.getSessionId()
                : UUID.randomUUID().toString();
        request.setSessionId(sessionId);

        ChatSession chatSession = chatSessionRepository.findBySessionId(sessionId)
                .map(existingSession -> {
                    if (!existingSession.getAccount().getId().equals(account.getId())) {
                        throw new InvalidDataException("Session does not belong to current user");
                    }
                    return existingSession;
                })
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder()
                        .account(account)
                        .sessionId(sessionId)
                        .title(resolveConversationTitle(request, httpRequest))
                        .build()));

        if (!StringUtils.hasText(chatSession.getTitle())) {
            System.out.println("Failed to resolve conversation title, using fallback");
            chatSession.setTitle(fallbackConversationTitle(request.getQuestion()));
        }
        chatSession.setUpdatedAt(Instant.now());
        chatSessionRepository.save(chatSession);

        HttpHeaders headers = buildHeadersWithAuthorization(httpRequest.getHeader("Authorization"));
        HttpEntity<ChatbotRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<?> response = restTemplate.exchange(
                chatbotUrl(),
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
        result.put("title", chatSession.getTitle());
        result.put("answer", assistantContent);
        return ResponseEntity.ok(result);
    }

    private HttpHeaders buildHeadersWithAuthorization(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
        return headers;
    }

    private String resolveConversationTitle(ChatbotRequest request, HttpServletRequest httpRequest) {
        try {
            System.out.println("Title request "+ request.getQuestion());
            System.out.println("Resolve conversation title URL: " + chatSetNameUrl());
            Map<String, String> body = new HashMap<>();
            body.put("message", request.getQuestion());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body);
            ResponseEntity<?> response = restTemplate.exchange(
                    chatSetNameUrl(),
                    HttpMethod.POST,
                    entity,
                    Object.class
            );
            String title = extractConversationTitle(response.getBody());
            System.out.println("Extracted title from chatbot response: " + title);
            if (StringUtils.hasText(title)) {
                return trimTitle(title);
            }
        } catch (Exception e) {
            System.out.println("Failed to resolve conversation title from " + chatSetNameUrl() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return fallbackConversationTitle(request.getQuestion());
    }

    private String chatbotUrl() {
        return StringUtils.hasText(configuredChatbotUrl) ? configuredChatbotUrl : DEFAULT_CHATBOT_URL;
    }

    private String chatSetNameUrl() {
        return StringUtils.hasText(configuredChatSetNameUrl) ? configuredChatSetNameUrl : DEFAULT_CHATBOT_TITLE_URL;
    }

    private String extractAssistantContent(Object responseBody) {
        if (responseBody instanceof Map<?, ?> map) {
            Object answer = map.get("answer");
            if (answer == null) {
                answer = map.get("response");
            }
            if (answer == null) {
                answer = map.get("message");
            }
            if (answer == null) {
                answer = map.get("title");
            }
            if (answer != null) {
                return String.valueOf(answer);
            }
        }

        return responseBody == null ? "" : String.valueOf(responseBody);
    }

    private String extractConversationTitle(Object responseBody) {
        if (responseBody instanceof Map<?, ?> map) {
            Object title = map.get("title");
            if (title == null) {
                title = map.get("answer");
            }
            if (title == null) {
                title = map.get("response");
            }
            if (title == null) {
                title = map.get("message");
            }
            if (title != null) {
                return String.valueOf(title).trim();
            }
        }

        return responseBody == null ? "" : String.valueOf(responseBody).trim();
    }

    private String fallbackConversationTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return DEFAULT_TITLE;
        }
        return trimTitle(question.trim().replaceAll("\\s+", " "));
    }

    private String trimTitle(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        Account account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<ChatSession> chatSessions = chatSessionRepository.findByAccountOrderByUpdatedAtDesc(account);
        int start = Math.min(page * size, chatSessions.size());
        int end = Math.min(start + size, chatSessions.size());
        List<ChatSession> pagedSessions = chatSessions.subList(start, end);

        List<ChatSessionResponse> sessions = pagedSessions.stream()
                .map(session -> chatMessageRepository.findFirstByAccountIdAndSessionIdAndRoleOrderByCreatedAtAsc(
                                account.getId(),
                                session.getSessionId(),
                                "user"
                        )
                        .map(firstUserMessage -> ChatSessionResponse.builder()
                                .sessionId(session.getSessionId())
                                .title(session.getTitle())
                                .lastMessageTime(firstUserMessage.getCreatedAt())
                                .lastMessage(firstUserMessage.getContent())
                                .lastRole(firstUserMessage.getRole())
                                .updatedAt(session.getUpdatedAt())
                                .build())
                        .orElseGet(() -> ChatSessionResponse.builder()
                                .sessionId(session.getSessionId())
                                .title(session.getTitle())
                                .updatedAt(session.getUpdatedAt())
                                .build()))
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

        Account account = accountRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        ChatSession chatSession = chatSessionRepository.findByAccountAndSessionId(account, sessionId)
                .orElse(null);
        if (chatSession == null || !chatMessageRepository.existsByAccountIdAndSessionId(account.getId(), sessionId)) {
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
                .title(chatSession.getTitle())
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
