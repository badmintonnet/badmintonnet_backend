package com.tlcn.sportsnet_backend.dto.recommendation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PersonalizedRecommendationResponse {
    RecommendationProfileResponse profile;
    List<RecommendationItemResponse> clubs;
    List<RecommendationItemResponse> events;
    List<RecommendationItemResponse> tournaments;
    Instant generatedAt;
}
