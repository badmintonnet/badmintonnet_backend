package com.tlcn.sportsnet_backend.dto.chatbot;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatSessionResponse {
    String sessionId;
    Instant lastMessageTime;
    String lastMessage;
    String lastRole;
    Instant updatedAt;
}

