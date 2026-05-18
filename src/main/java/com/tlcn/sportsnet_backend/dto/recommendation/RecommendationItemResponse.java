package com.tlcn.sportsnet_backend.dto.recommendation;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationItemResponse {
    String id;
    String slug;
    String type;
    String title;
    String subtitle;
    String imageUrl;
    String location;
    String detailUrl;
    String clubName;
    String status;
    FacilityResponse facility;
    Double score;
    Double distanceKm;
    Double minLevel;
    Double maxLevel;
    Integer totalSlots;
    Integer joinedSlots;
    BigDecimal fee;
    LocalDateTime startTime;
    LocalDateTime endTime;
    LocalDateTime registrationEndDate;
    Set<String> tags;
    List<String> categories;
    List<String> reasons;
}
