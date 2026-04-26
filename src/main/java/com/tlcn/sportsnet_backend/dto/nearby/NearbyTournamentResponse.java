package com.tlcn.sportsnet_backend.dto.nearby;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NearbyTournamentResponse {
    String id;
    String slug;
    String name;
    String logoUrl;
    String bannerUrl;
    String location;
    LocalDateTime startDate;
    LocalDateTime endDate;
    FacilityResponse facility;
    Double distanceKm;
}

