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

    /** Số tie đã đấu (cặp đấu CLB-vs-CLB). */
    Integer played;
    /** Số tie thắng. */
    Integer wins;
    /** Số tie thua. */
    Integer losses;
    /** Tổng set thắng. */
    Integer setsWon;
    /** Tổng set thua. */
    Integer setsLost;

    // ===== Tie-mode extras =====
    /** Tổng rubber thắng (chi tiết hơn wins). */
    Integer rubberWins;
    /** Tổng rubber thua. */
    Integer rubberLosses;
}
