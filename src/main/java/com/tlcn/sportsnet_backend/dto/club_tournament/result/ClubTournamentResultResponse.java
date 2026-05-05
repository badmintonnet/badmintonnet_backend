package com.tlcn.sportsnet_backend.dto.club_tournament.result;

import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubTournamentResultResponse {
    String tournamentId;
    String tournamentName;
    TournamentStatus status;

    boolean finished;
    Integer totalClubs;

    List<ClubResultPodiumItem> podium;
    List<ClubResultPodiumItem> ranking;
    List<ClubResultMatchSummary> keyMatches;
    List<ClubResultClubStat> clubStats;
}
