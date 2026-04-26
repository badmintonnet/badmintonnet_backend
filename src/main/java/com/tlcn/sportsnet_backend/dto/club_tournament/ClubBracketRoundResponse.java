package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketRoundResponse {
    int round;
    List<ClubBracketMatchResponse> matches;
}
