package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketResponse {
    String tournamentId;
    String tournamentName;
    String categoryId;
    String categoryName;
    int totalRounds;
    List<ClubBracketRoundResponse> rounds;
}
