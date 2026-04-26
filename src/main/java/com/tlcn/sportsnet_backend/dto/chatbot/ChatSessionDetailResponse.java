package com.tlcn.sportsnet_backend.dto.chatbot;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatSessionDetailResponse {
    String sessionId;
    List<ChatMessageResponse> messages;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean last;
}

