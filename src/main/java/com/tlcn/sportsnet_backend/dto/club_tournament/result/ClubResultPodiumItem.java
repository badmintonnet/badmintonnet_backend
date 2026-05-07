package com.tlcn.sportsnet_backend.dto.club_tournament.result;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResultPodiumItem {
    Integer ranking;
    String prize;

    String participantId;
    String clubId;
    String clubName;
    String clubLogoUrl;

    String representativeName;
    String representativeAvatarUrl;
}
