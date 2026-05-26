package com.tlcn.sportsnet_backend.dto.club_tournament.result;

import com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResultRubberSummary {
    String matchId;
    ClubLineTypeEnum lineType;
    Integer lineIndex;
    String label;

    /** Tên rút gọn 2 phía (e.g. "CLB A - Tên A" hoặc "CLB A - Nam / Hùng"). */
    String side1Name;
    String side2Name;

    List<Integer> setScoreP1;
    List<Integer> setScoreP2;

    /** ID CLB thắng rubber này. */
    String winnerClubParticipantId;
    String status;
}
