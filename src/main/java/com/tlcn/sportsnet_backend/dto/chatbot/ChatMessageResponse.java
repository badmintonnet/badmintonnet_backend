package com.tlcn.sportsnet_backend.dto.chatbot;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageResponse {
    String id;
    String sessionId;
    String role;
    String content;
    Instant createdAt;
}

