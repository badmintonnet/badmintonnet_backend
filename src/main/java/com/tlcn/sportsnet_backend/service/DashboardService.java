package com.tlcn.sportsnet_backend.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.sportsnet_backend.dto.dashboard.AdminDashboardOverviewResponse;
import com.tlcn.sportsnet_backend.dto.dashboard.ClubDashboardResponse;
import com.tlcn.sportsnet_backend.dto.dashboard.DashboardPeriod;
import com.tlcn.sportsnet_backend.dto.dashboard.DashboardPoint;
import com.tlcn.sportsnet_backend.dto.dashboard.StatusCount;
import com.tlcn.sportsnet_backend.dto.dashboard.TopClubMetric;
import com.tlcn.sportsnet_backend.dto.dashboard.TopEventMetric;
import com.tlcn.sportsnet_backend.dto.dashboard.TopMemberMetric;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubEvent;
import com.tlcn.sportsnet_backend.entity.ClubEventParticipant;
import com.tlcn.sportsnet_backend.entity.ClubEventRating;
import com.tlcn.sportsnet_backend.entity.ClubMember;
import com.tlcn.sportsnet_backend.entity.Tournament;
import com.tlcn.sportsnet_backend.entity.TournamentPayment;
import com.tlcn.sportsnet_backend.enums.ClubEventParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.enums.ClubStatusEnum;
import com.tlcn.sportsnet_backend.enums.EventStatusEnum;
import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.error.ResourceNotFoundException;
import com.tlcn.sportsnet_backend.error.UnauthorizedException;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.ClubEventParticipantRepository;
import com.tlcn.sportsnet_backend.repository.ClubEventRatingRepository;
import com.tlcn.sportsnet_backend.repository.ClubEventRepository;
import com.tlcn.sportsnet_backend.repository.ClubMemberRepository;
import com.tlcn.sportsnet_backend.repository.ClubRepository;
import com.tlcn.sportsnet_backend.repository.TournamentParticipantRepository;
import com.tlcn.sportsnet_backend.repository.TournamentPaymentRepository;
import com.tlcn.sportsnet_backend.repository.TournamentRepository;
import com.tlcn.sportsnet_backend.repository.TournamentTeamRepository;
import com.tlcn.sportsnet_backend.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private final AccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final ClubEventRepository clubEventRepository;
    private final ClubEventParticipantRepository participantRepository;
    private final ClubEventRatingRepository ratingRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentPaymentRepository tournamentPaymentRepository;

    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getAdminOverview(LocalDate from, LocalDate to, DashboardPeriod period) {
        requireAdmin();
        TimeWindow window = resolveWindow(from, to, period);

        List<Account> accounts = accountRepository.findAll();
        List<Club> clubs = clubRepository.findAll();
        List<ClubEvent> events = clubEventRepository.findAll();
        List<ClubEventParticipant> participants = participantRepository.findAll();
        List<ClubEventRating> ratings = ratingRepository.findAll();
        List<ClubMember> members = clubMemberRepository.findAll();
        List<Tournament> tournaments = tournamentRepository.findAll();
        List<TournamentPayment> payments = tournamentPaymentRepository.findAll();

        List<ClubEvent> eventsInWindow = events.stream()
                .filter(event -> isInWindow(event.getCreatedAt(), window))
                .toList();
        List<ClubEventParticipant> participantsInWindow = participants.stream()
                .filter(participant -> isInWindow(participant.getJoinedAt(), window))
                .toList();
        List<Tournament> tournamentsInWindow = tournaments.stream()
                .filter(tournament -> isInWindow(tournament.getCreatedAt(), window))
                .toList();
        List<TournamentPayment> paymentsInWindow = payments.stream()
                .filter(payment -> isInWindow(payment.getCreatedAt(), window))
                .toList();

        return AdminDashboardOverviewResponse.builder()
                .fromDate(window.fromDate())
                .toDate(window.toDate())
                .period(window.period())
                .userGrowth(buildAdminUserGrowth(accounts, window))
                .revenue(buildAdminRevenue(paymentsInWindow, window))
                .eventActivity(buildAdminEventActivity(clubs, eventsInWindow, participantsInWindow))
                .clubStatistics(buildAdminClubStatistics(clubs, events, ratings, members))
                .tournamentStatistics(buildAdminTournamentStatistics(tournamentsInWindow, paymentsInWindow, window))
                .build();
    }

    @Transactional(readOnly = true)
    public ClubDashboardResponse getClubDashboard(String clubIdOrSlug, LocalDate from, LocalDate to,
            DashboardPeriod period) {
        Club club = resolveClub(clubIdOrSlug);
        requireClubOwnerOrAdmin(club);
        TimeWindow window = resolveWindow(from, to, period);
        String clubId = club.getId();

        List<ClubMember> members = clubMemberRepository.findByClubId(clubId);
        List<ClubEvent> clubEvents = clubEventRepository.findAll().stream()
                .filter(event -> event.getClub() != null && clubId.equals(event.getClub().getId()))
                .toList();
        List<ClubEventParticipant> participants = participantRepository.findAll().stream()
                .filter(participant -> participant.getClubEvent() != null
                        && participant.getClubEvent().getClub() != null
                        && clubId.equals(participant.getClubEvent().getClub().getId()))
                .toList();
        List<ClubEventRating> ratings = ratingRepository.findAll().stream()
                .filter(rating -> rating.getClubEvent() != null
                        && rating.getClubEvent().getClub() != null
                        && clubId.equals(rating.getClubEvent().getClub().getId()))
                .toList();

        List<ClubEventParticipant> participantsInWindow = participants.stream()
                .filter(participant -> isInWindow(participant.getJoinedAt(), window))
                .toList();
        List<ClubEvent> eventsInWindow = clubEvents.stream()
                .filter(event -> isInWindow(event.getCreatedAt(), window))
                .toList();
        List<ClubEventRating> ratingsInWindow = ratings.stream()
                .filter(rating -> isInWindow(rating.getCreatedAt(), window))
                .toList();

        return ClubDashboardResponse.builder()
                .clubId(clubId)
                .clubSlug(club.getSlug())
                .clubName(club.getName())
                .fromDate(window.fromDate())
                .toDate(window.toDate())
                .period(window.period())
                .memberGrowth(buildClubMemberGrowth(members, window))
                .attendanceRate(buildClubAttendance(participantsInWindow))
                .engagement(buildClubEngagement(clubEvents, eventsInWindow, participantsInWindow, ratingsInWindow))
                .build();
    }

    private AdminDashboardOverviewResponse.UserGrowth buildAdminUserGrowth(List<Account> accounts, TimeWindow window) {
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        Instant todayStart = today.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(DASHBOARD_ZONE).toInstant();

        return AdminDashboardOverviewResponse.UserGrowth.builder()
                .totalUsers(accounts.size())
                .newUsersToday(countSince(accounts, Account::getCreatedAt, todayStart))
                .newUsersThisWeek(countSince(accounts, Account::getCreatedAt, weekStart))
                .newUsersThisMonth(countSince(accounts, Account::getCreatedAt, monthStart))
                .activeUsers(accounts.stream().filter(Account::isEnabled).count())
                .growth(buildSeries(accounts, Account::getCreatedAt, account -> 1D, window))
                .build();
    }

    private AdminDashboardOverviewResponse.Revenue buildAdminRevenue(List<TournamentPayment> paymentsInWindow,
            TimeWindow window) {
        List<TournamentPayment> successfulPayments = paymentsInWindow.stream()
                .filter(payment -> payment.getStatus() == PaymentStatusEnum.SUCCESS)
                .toList();

        return AdminDashboardOverviewResponse.Revenue.builder()
                .totalRevenue(successfulPayments.stream().mapToDouble(payment -> safeDouble(payment.getAmount())).sum())
                .successfulTransactions(successfulPayments.size())
                .failedTransactions(paymentsInWindow.stream()
                        .filter(payment -> payment.getStatus() == PaymentStatusEnum.FAILED).count())
                .revenueByPeriod(buildSeries(successfulPayments, TournamentPayment::getCreatedAt,
                        payment -> safeDouble(payment.getAmount()), window))
                .build();
    }

    private AdminDashboardOverviewResponse.EventActivity buildAdminEventActivity(List<Club> clubs,
            List<ClubEvent> eventsInWindow, List<ClubEventParticipant> participantsInWindow) {
        Map<String, Long> eventCountByClub = eventsInWindow.stream()
                .filter(event -> event.getClub() != null)
                .collect(Collectors.groupingBy(event -> event.getClub().getId(), Collectors.counting()));

        return AdminDashboardOverviewResponse.EventActivity.builder()
                .totalEvents(eventsInWindow.size())
                .statusCounts(statusCounts(EventStatusEnum.class, eventsInWindow, ClubEvent::getStatus))
                .totalParticipations(participantsInWindow.size())
                .topClubsByEvents(topClubs(clubs, club -> eventCountByClub.getOrDefault(club.getId(), 0L), 5))
                .build();
    }

    private AdminDashboardOverviewResponse.ClubStatistics buildAdminClubStatistics(List<Club> clubs,
            List<ClubEvent> events, List<ClubEventRating> ratings, List<ClubMember> members) {
        Map<String, Long> eventCountByClub = events.stream()
                .filter(event -> event.getClub() != null)
                .collect(Collectors.groupingBy(event -> event.getClub().getId(), Collectors.counting()));
        Map<String, Double> ratingByClub = ratings.stream()
                .filter(rating -> rating.getClubEvent() != null && rating.getClubEvent().getClub() != null)
                .collect(Collectors.groupingBy(rating -> rating.getClubEvent().getClub().getId(),
                        Collectors.averagingDouble(rating -> safeDouble(rating.getRating()))));
        Map<String, Long> approvedMemberCountByClub = members.stream()
                .filter(member -> member.getClub() != null)
                .filter(member -> member.getStatus() == ClubMemberStatusEnum.APPROVED)
                .collect(Collectors.groupingBy(member -> member.getClub().getId(), Collectors.counting()));

        return AdminDashboardOverviewResponse.ClubStatistics.builder()
                .totalClubs(clubs.size())
                .statusCounts(statusCounts(ClubStatusEnum.class, clubs, Club::getStatus))
                .topClubsByReputation(topClubs(clubs,
                        club -> Math.max(safeDouble(club.getReputation()), ratingByClub.getOrDefault(club.getId(), 0D)),
                        5))
                .topClubsByMembers(topClubs(clubs, club -> approvedMemberCountByClub.getOrDefault(club.getId(), 0L), 5))
                .topClubsByEvents(topClubs(clubs, club -> eventCountByClub.getOrDefault(club.getId(), 0L), 5))
                .build();
    }

    private AdminDashboardOverviewResponse.TournamentStatistics buildAdminTournamentStatistics(
            List<Tournament> tournamentsInWindow, List<TournamentPayment> paymentsInWindow, TimeWindow window) {
        long participantRegistrations = tournamentParticipantRepository.findAll().stream()
                .filter(participant -> isInWindow(participant.getCreatedAt(), window))
                .count();
        long teamRegistrations = tournamentTeamRepository.findAll().stream()
                .filter(team -> isInWindow(team.getCreatedAt(), window))
                .count();

        return AdminDashboardOverviewResponse.TournamentStatistics.builder()
                .totalTournaments(tournamentsInWindow.size())
                .statusCounts(statusCounts(TournamentStatus.class, tournamentsInWindow, Tournament::getStatus))
                .totalRegistrations(participantRegistrations + teamRegistrations)
                .totalSuccessfulPayments(paymentsInWindow.stream()
                        .filter(payment -> payment.getStatus() == PaymentStatusEnum.SUCCESS).count())
                .build();
    }

    private ClubDashboardResponse.MemberGrowth buildClubMemberGrowth(List<ClubMember> members, TimeWindow window) {
        List<ClubMember> approvedMembers = members.stream()
                .filter(member -> member.getStatus() == ClubMemberStatusEnum.APPROVED)
                .toList();

        return ClubDashboardResponse.MemberGrowth.builder()
                .totalMembers(approvedMembers.size())
                .newMembers(approvedMembers.stream().filter(member -> isInWindow(member.getJoinedAt(), window)).count())
                .pendingMembers(members.stream().filter(member -> member.getStatus() == ClubMemberStatusEnum.PENDING)
                        .count())
                .bannedMembers(members.stream().filter(member -> member.getStatus() == ClubMemberStatusEnum.BANNED)
                        .count())
                .growth(buildSeries(approvedMembers, ClubMember::getJoinedAt, member -> 1D, window))
                .build();
    }

    private ClubDashboardResponse.AttendanceRate buildClubAttendance(
            List<ClubEventParticipant> participantsInWindow) {
        long attended = countByParticipantStatus(participantsInWindow, ClubEventParticipantStatusEnum.ATTENDED);
        long absent = countByParticipantStatus(participantsInWindow, ClubEventParticipantStatusEnum.ABSENT);
        long cancelled = participantsInWindow.stream()
                .filter(participant -> participant.getStatus() == ClubEventParticipantStatusEnum.CANCELLED
                        || participant.getStatus() == ClubEventParticipantStatusEnum.CANCELLATION_PENDING)
                .count();
        long approved = participantsInWindow.stream()
                .filter(participant -> participant.getStatus() == ClubEventParticipantStatusEnum.APPROVED
                        || participant.getStatus() == ClubEventParticipantStatusEnum.ATTENDED
                        || participant.getStatus() == ClubEventParticipantStatusEnum.ABSENT)
                .count();

        return ClubDashboardResponse.AttendanceRate.builder()
                .totalRegistrations(participantsInWindow.size())
                .approved(approved)
                .attended(attended)
                .absent(absent)
                .cancelled(cancelled)
                .attendanceRate(approved == 0 ? 0 : roundTwoDecimals((double) attended / approved * 100))
                .build();
    }

    private ClubDashboardResponse.Engagement buildClubEngagement(List<ClubEvent> clubEvents,
            List<ClubEvent> eventsInWindow, List<ClubEventParticipant> participantsInWindow,
            List<ClubEventRating> ratingsInWindow) {
        LocalDate now = LocalDate.now(DASHBOARD_ZONE);
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant nextMonthStart = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(DASHBOARD_ZONE).toInstant();

        Map<String, Long> participantCountByEvent = participantsInWindow.stream()
                .filter(this::isMeaningfulParticipation)
                .collect(Collectors.groupingBy(participant -> participant.getClubEvent().getId(), Collectors.counting()));

        double averageParticipantsPerEvent = eventsInWindow.isEmpty() ? 0
                : roundTwoDecimals((double) participantCountByEvent.values().stream().mapToLong(Long::longValue).sum()
                        / eventsInWindow.size());
        double averageRating = ratingsInWindow.isEmpty() ? 0
                : roundTwoDecimals(ratingsInWindow.stream().mapToDouble(rating -> safeDouble(rating.getRating()))
                        .average().orElse(0));

        return ClubDashboardResponse.Engagement.builder()
                .eventCount(clubEvents.size())
                .eventsThisMonth(clubEvents.stream()
                        .filter(event -> isInRange(event.getCreatedAt(), monthStart, nextMonthStart)).count())
                .averageParticipantsPerEvent(averageParticipantsPerEvent)
                .averageRating(averageRating)
                .totalRating(ratingsInWindow.size())
                .topActiveMembers(topActiveMembers(participantsInWindow, 5))
                .topEvents(buildTopEvents(eventsInWindow, participantCountByEvent, 5))
                .build();
    }

    private List<TopMemberMetric> topActiveMembers(List<ClubEventParticipant> participants, int limit) {
        return participants.stream()
                .filter(this::isMeaningfulParticipation)
                .filter(participant -> participant.getParticipant() != null)
                .collect(Collectors.groupingBy(ClubEventParticipant::getParticipant, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Account, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> TopMemberMetric.builder()
                        .id(entry.getKey().getId())
                        .slug(entry.getKey().getUserInfo() == null ? null : entry.getKey().getUserInfo().getSlug())
                        .name(entry.getKey().getUserInfo() == null ? entry.getKey().getEmail()
                                : entry.getKey().getUserInfo().getFullName())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private List<TopEventMetric> buildTopEvents(List<ClubEvent> events, Map<String, Long> participantCountByEvent,
            int limit) {
        return events.stream()
                .sorted(Comparator.comparing((ClubEvent event) -> participantCountByEvent.getOrDefault(event.getId(), 0L))
                        .reversed())
                .limit(limit)
                .map(event -> TopEventMetric.builder()
                        .id(event.getId())
                        .slug(event.getSlug())
                        .title(event.getTitle())
                        .value(participantCountByEvent.getOrDefault(event.getId(), 0L))
                        .build())
                .toList();
    }

    private List<TopClubMetric> topClubs(List<Club> clubs, Function<Club, Number> valueFunction, int limit) {
        return clubs.stream()
                .sorted(Comparator.comparing((Club club) -> valueFunction.apply(club).doubleValue()).reversed())
                .limit(limit)
                .map(club -> TopClubMetric.builder()
                        .id(club.getId())
                        .slug(club.getSlug())
                        .name(club.getName())
                        .value(roundTwoDecimals(valueFunction.apply(club).doubleValue()))
                        .build())
                .toList();
    }

    private <T, E extends Enum<E>> List<StatusCount> statusCounts(Class<E> enumClass, List<T> items,
            Function<T, E> statusExtractor) {
        Map<E, Long> counts = items.stream()
                .filter(item -> statusExtractor.apply(item) != null)
                .collect(Collectors.groupingBy(statusExtractor, () -> new EnumMap<>(enumClass), Collectors.counting()));

        List<StatusCount> result = new ArrayList<>();
        for (E status : enumClass.getEnumConstants()) {
            result.add(StatusCount.builder()
                    .status(status.name())
                    .count(counts.getOrDefault(status, 0L))
                    .build());
        }
        return result;
    }

    private <T> List<DashboardPoint> buildSeries(List<T> items, Function<T, Instant> instantExtractor,
            Function<T, Double> valueExtractor, TimeWindow window) {
        Map<LocalDate, Double> values = items.stream()
                .filter(item -> isInWindow(instantExtractor.apply(item), window))
                .collect(Collectors.groupingBy(
                        item -> bucketStart(toLocalDate(instantExtractor.apply(item)), window.period()),
                        LinkedHashMap::new,
                        Collectors.summingDouble(valueExtractor::apply)));

        List<DashboardPoint> series = new ArrayList<>();
        LocalDate cursor = bucketStart(window.fromDate(), window.period());
        LocalDate end = bucketStart(window.toDate(), window.period());
        while (!cursor.isAfter(end)) {
            series.add(DashboardPoint.builder()
                    .label(formatLabel(cursor, window.period()))
                    .value(roundTwoDecimals(values.getOrDefault(cursor, 0D)))
                    .build());
            cursor = nextBucket(cursor, window.period());
        }
        return series;
    }

    private Club resolveClub(String clubIdOrSlug) {
        Optional<Club> byId = clubRepository.findById(clubIdOrSlug);
        return byId.or(() -> clubRepository.findBySlug(clubIdOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu lạc bộ"));
    }

    private void requireAdmin() {
        if (!hasAuthority("ROLE_ADMIN")) {
            throw new UnauthorizedException("Bạn không có quyền truy cập dashboard quản trị");
        }
    }

    private void requireClubOwnerOrAdmin(Club club) {
        if (hasAuthority("ROLE_ADMIN")) {
            return;
        }

        String currentEmail = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để truy cập dashboard câu lạc bộ"));
        if (club.getOwner() == null || !currentEmail.equals(club.getOwner().getEmail())) {
            throw new UnauthorizedException("Bạn không có quyền truy cập dashboard của câu lạc bộ này");
        }
    }

    private boolean hasAuthority(String authority) {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private TimeWindow resolveWindow(LocalDate from, LocalDate to, DashboardPeriod period) {
        DashboardPeriod safePeriod = period == null ? DashboardPeriod.DAY : period;
        LocalDate safeTo = to == null ? LocalDate.now(DASHBOARD_ZONE) : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(29) : from;
        if (safeFrom.isAfter(safeTo)) {
            throw new InvalidDataException("Ngày bắt đầu không được sau ngày kết thúc");
        }
        return new TimeWindow(
                safeFrom,
                safeTo,
                safePeriod,
                safeFrom.atStartOfDay(DASHBOARD_ZONE).toInstant(),
                safeTo.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant());
    }

    private long countSince(List<Account> accounts, Function<Account, Instant> instantExtractor, Instant from) {
        return accounts.stream()
                .filter(account -> instantExtractor.apply(account) != null)
                .filter(account -> !instantExtractor.apply(account).isBefore(from))
                .count();
    }

    private long countApprovedMembers(Collection<ClubMember> members) {
        return members.stream()
                .filter(member -> member.getStatus() == ClubMemberStatusEnum.APPROVED)
                .count();
    }

    private long countByParticipantStatus(List<ClubEventParticipant> participants,
            ClubEventParticipantStatusEnum status) {
        return participants.stream()
                .filter(participant -> participant.getStatus() == status)
                .count();
    }

    private boolean isMeaningfulParticipation(ClubEventParticipant participant) {
        return participant.getStatus() == ClubEventParticipantStatusEnum.APPROVED
                || participant.getStatus() == ClubEventParticipantStatusEnum.ATTENDED
                || participant.getStatus() == ClubEventParticipantStatusEnum.ABSENT;
    }

    private boolean isInWindow(Instant instant, TimeWindow window) {
        return isInRange(instant, window.fromInstant(), window.toExclusive());
    }

    private boolean isInRange(Instant instant, Instant fromInclusive, Instant toExclusive) {
        return instant != null && !instant.isBefore(fromInclusive) && instant.isBefore(toExclusive);
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(DASHBOARD_ZONE).toLocalDate();
    }

    private LocalDate bucketStart(LocalDate date, DashboardPeriod period) {
        return switch (period) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private LocalDate nextBucket(LocalDate date, DashboardPeriod period) {
        return switch (period) {
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
            default -> date.plusDays(1);
        };
    }

    private String formatLabel(LocalDate date, DashboardPeriod period) {
        return switch (period) {
            case WEEK -> "Tuần " + date.format(DAY_FORMATTER);
            case MONTH -> date.format(MONTH_FORMATTER);
            default -> date.format(DAY_FORMATTER);
        };
    }

    private double safeDouble(Number value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record TimeWindow(LocalDate fromDate, LocalDate toDate, DashboardPeriod period, Instant fromInstant,
            Instant toExclusive) {
    }
}
