package com.tlcn.sportsnet_backend.dto.club_tournament.result;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResultClubStat {
    String participantId;
    String clubId;
    String clubName;
    String clubLogoUrl;

    Integer played;
    Integer wins;
    Integer losses;
    Integer setsWon;
    Integer setsLost;
}
