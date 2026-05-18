package com.tlcn.sportsnet_backend.dto.recommendation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationProfileResponse {
    String fullName;
    Double skillScore;
    String skillLevel;
    boolean hasLocation;
    List<String> favoriteCategories;
    List<String> preferredTimeSlots;
}
