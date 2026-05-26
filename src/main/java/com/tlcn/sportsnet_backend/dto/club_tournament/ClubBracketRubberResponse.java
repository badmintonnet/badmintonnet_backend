package com.tlcn.sportsnet_backend.dto.club_tournament;

import com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * 1 rubber (ván) trong tie. Tương đương 1 TournamentMatch ở DB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketRubberResponse {
    String matchId;
    ClubLineTypeEnum lineType;
    Integer lineIndex;
    /** Label hiển thị, e.g. "Đơn #1", "Đôi nam #2". */
    String label;

    /** Player(s) phía CLB 1: list 1 phần tử cho singles, 2 cho doubles. */
    List<ClubMatchParticipantResponse> club1Players;
    List<ClubMatchParticipantResponse> club2Players;

    List<Integer> setScoreP1;
    List<Integer> setScoreP2;

    String winnerClubParticipantId;
    String status;
}
