package com.tlcn.sportsnet_backend.dto.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRankingResponse {
    private int rank;
    private String accountId;
    private String slug;
    private String fullName;
    private String avatarUrl;
    private String address;
    private double competitiveScore;
    private double tournamentScore;
    private double winRateScore;
    private double matchVolumeScore;
    private int reputationScore;
    private int totalMatches;
    private int totalWins;
    private double winRate;
    private int completedTournaments;
    private boolean provisional;
}
