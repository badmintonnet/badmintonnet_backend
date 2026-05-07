package com.tlcn.sportsnet_backend.dto.club_tournament.result;

import com.tlcn.sportsnet_backend.dto.club_tournament.ClubMatchParticipantResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubResultMatchSummary {
    String matchId;
    String label;
    Integer round;
    Integer matchIndex;

    ClubMatchParticipantResponse player1;
    ClubMatchParticipantResponse player2;

    /**
     * Legacy: list điểm từng set (1 trận đơn).
     * Tie-mode: chỉ là tổng tham khảo, không dùng để hiển thị chính.
     */
    List<Integer> setScoreP1;
    List<Integer> setScoreP2;

    String winnerId;
    String winnerName;
    String status;

    // ===== Tie-mode fields (null cho legacy) =====
    String tieId;
    /** Rubber wins. */
    Integer club1RubberWins;
    Integer club2RubberWins;
    /** Tổng set wins (dùng làm tie-breaker). */
    Integer club1SetsWon;
    Integer club2SetsWon;
    /** Chi tiết từng rubber. */
    List<ClubResultRubberSummary> rubbers;
}
