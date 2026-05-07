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
public class ClubDashboardResponse {
    private String clubId;
    private String clubSlug;
    private String clubName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private DashboardPeriod period;
    private MemberGrowth memberGrowth;
    private AttendanceRate attendanceRate;
    private Engagement engagement;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberGrowth {
        private long totalMembers;
        private long newMembers;
        private long pendingMembers;
        private long bannedMembers;
        private List<DashboardPoint> growth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceRate {
        private long totalRegistrations;
        private long approved;
        private long attended;
        private long absent;
        private long cancelled;
        private double attendanceRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Engagement {
        private long eventCount;
        private long eventsThisMonth;
        private double averageParticipantsPerEvent;
        private double averageRating;
        private long totalRating;
        private List<TopMemberMetric> topActiveMembers;
        private List<TopEventMetric> topEvents;
    }
}
