package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * 1 tie = 1 cặp đấu CLB-vs-CLB trong 1 round, gồm N rubber.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketTieResponse {
    /** UUID nhóm các rubber (TournamentMatch.tieId). null cho legacy single-rubber. */
    String tieId;

    int round;
    int matchIndex;

    ClubMatchParticipantResponse club1;
    ClubMatchParticipantResponse club2;

    /** Số rubber thắng của mỗi CLB (đếm từ rubber FINISHED). */
    int club1RubberWins;
    int club2RubberWins;

    /** Total set won (dùng làm tie-breaker khi rubber wins hòa). */
    int club1SetsWon;
    int club2SetsWon;

    /** ID CLB thắng tie (null nếu chưa xong). */
    String winnerClubParticipantId;

    /** Aggregate status: NOT_STARTED / IN_PROGRESS / FINISHED. */
    String status;

    List<ClubBracketRubberResponse> rubbers;
}
