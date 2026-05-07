package com.tlcn.sportsnet_backend.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardOverviewResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private DashboardPeriod period;
    private UserGrowth userGrowth;
    private Revenue revenue;
    private EventActivity eventActivity;
    private ClubStatistics clubStatistics;
    private TournamentStatistics tournamentStatistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserGrowth {
        private long totalUsers;
        private long newUsersToday;
        private long newUsersThisWeek;
        private long newUsersThisMonth;
        private long activeUsers;
        private List<DashboardPoint> growth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Revenue {
        private double totalRevenue;
        private long successfulTransactions;
        private long failedTransactions;
        private List<DashboardPoint> revenueByPeriod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventActivity {
        private long totalEvents;
        private List<StatusCount> statusCounts;
        private long totalParticipations;
        private List<TopClubMetric> topClubsByEvents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClubStatistics {
        private long totalClubs;
        private List<StatusCount> statusCounts;
        private List<TopClubMetric> topClubsByReputation;
        private List<TopClubMetric> topClubsByMembers;
        private List<TopClubMetric> topClubsByEvents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TournamentStatistics {
        private long totalTournaments;
        private List<StatusCount> statusCounts;
        private long totalRegistrations;
        private long totalSuccessfulPayments;
    }
}
