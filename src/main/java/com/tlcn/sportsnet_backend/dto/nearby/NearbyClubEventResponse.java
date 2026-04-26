package com.tlcn.sportsnet_backend.dto.nearby;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NearbyClubEventResponse {
    String id;
    String slug;
    String title;
    String image;
    String location;
    LocalDateTime startTime;
    LocalDateTime endTime;
    BigDecimal fee;
    FacilityResponse facility;
    Double distanceKm;
}

