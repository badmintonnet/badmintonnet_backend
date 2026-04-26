package com.tlcn.sportsnet_backend.dto.nearby;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NearbyClubResponse {
    String id;
    String slug;
    String name;
    String logoUrl;
    String location;
    FacilityResponse facility;
    Double distanceKm;
}

