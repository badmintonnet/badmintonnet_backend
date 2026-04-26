package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketMatchResponse {
    String matchId;
    int round;
    int matchIndex;
    ClubMatchParticipantResponse player1;
    ClubMatchParticipantResponse player2;
    List<Integer> setScoreP1;
    List<Integer> setScoreP2;
    String winnerId;
    String winnerName;
    String status;
}
