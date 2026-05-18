package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.dto.recommendation.PersonalizedRecommendationResponse;
import com.tlcn.sportsnet_backend.dto.recommendation.RecommendationItemResponse;
import com.tlcn.sportsnet_backend.dto.recommendation.RecommendationProfileResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.*;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import com.tlcn.sportsnet_backend.util.GeoDistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private static final int CANDIDATE_LIMIT = 80;
    private static final int DEFAULT_TOP = 4;
    private static final int MAX_TOP = 8;

    private final AccountRepository accountRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final UserScheduleRepository userScheduleRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubEventRepository clubEventRepository;
    private final ClubEventParticipantRepository clubEventParticipantRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PersonalizedRecommendationResponse getPersonalizedRecommendations(int top) {
        int limit = normalizeTop(top);
        Account account = resolveCurrentAccount();
        PlayerRating rating = playerRatingRepository.findByAccount(account).orElse(account.getPlayerRating());
        List<UserSchedule> schedules = userScheduleRepository.findTop30ByAccount_IdOrderByStartTimeDesc(account.getId());
        List<ClubMember> memberships = clubMemberRepository.findByAccountAndStatus(account, ClubMemberStatusEnum.APPROVED);
        List<ClubEventParticipant> eventHistory =
                clubEventParticipantRepository.findTop30ByParticipant_IdAndStatusNotOrderByJoinedAtDesc(
                        account.getId(),
                        ClubEventParticipantStatusEnum.CANCELLED
                );
        List<TournamentParticipant> tournamentHistory =
                tournamentParticipantRepository.findTop20ByAccount_IdOrderByCreatedAtDesc(account.getId());

        UserPreference preference = buildPreference(
                account,
                rating,
                schedules,
                memberships,
                eventHistory,
                tournamentHistory
        );

        return PersonalizedRecommendationResponse.builder()
                .profile(buildProfile(account, rating, preference))
                .clubs(recommendClubs(preference, limit))
                .events(recommendEvents(account, preference, limit))
                .tournaments(recommendTournaments(account, preference, limit))
                .generatedAt(Instant.now())
                .build();
    }

    private List<RecommendationItemResponse> recommendClubs(UserPreference preference, int limit) {
        return clubRepository.findRecommendationCandidates(
                        ClubVisibilityEnum.PUBLIC,
                        ClubStatusEnum.ACTIVE,
                        PageRequest.of(0, CANDIDATE_LIMIT)
                )
                .stream()
                .filter(club -> !preference.joinedClubIds().contains(club.getId()))
                .map(club -> toClubRecommendation(club, preference))
                .sorted(Comparator.comparing(RecommendationItemResponse::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<RecommendationItemResponse> recommendEvents(Account account, UserPreference preference, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return clubEventRepository.findRecommendationCandidates(
                        EventStatusEnum.OPEN,
                        now,
                        PageRequest.of(0, CANDIDATE_LIMIT)
                )
                .stream()
                .filter(event -> event.isOpenForOutside()
                        || preference.joinedClubIds().contains(event.getClub().getId()))
                .filter(event -> !clubEventParticipantRepository
                        .existsByClubEventAndParticipantAndStatusNot(event, account, ClubEventParticipantStatusEnum.CANCELLED))
                .filter(event -> !hasScheduleConflict(preference.schedules(), event.getStartTime(), event.getEndTime()))
                .map(event -> toEventRecommendation(event, preference))
                .sorted(Comparator.comparing(RecommendationItemResponse::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<RecommendationItemResponse> recommendTournaments(Account account, UserPreference preference, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentStatus> excludedStatuses = List.of(TournamentStatus.CANCELLED, TournamentStatus.COMPLETED);
        return tournamentRepository.findRecommendationCandidates(
                        excludedStatuses,
                        now,
                        PageRequest.of(0, CANDIDATE_LIMIT)
                )
                .stream()
                .filter(tournament -> !hasJoinedTournament(account, tournament))
                .map(tournament -> toTournamentRecommendation(tournament, preference))
                .sorted(Comparator.comparing(RecommendationItemResponse::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private RecommendationItemResponse toClubRecommendation(Club club, UserPreference preference) {
        List<String> reasons = new ArrayList<>();
        Score score = new Score(18);

        score.add(levelScore(club.getMinLevel(), club.getMaxLevel(), preference.skillScore(), reasons));
        score.add(distanceScore(club.getFacility(), preference, reasons));

        if (hasSharedTags(club.getTags(), preference.favoriteTags())) {
            score.add(12);
            addReason(reasons, "Có phong cách giống CLB bạn từng quan tâm");
        }

        if (club.getReputation() != null && club.getReputation() >= 70) {
            score.add(10);
            addReason(reasons, "CLB có điểm uy tín cao");
        }

        if (club.getMaxMembers() <= 0) {
            score.add(4);
        }

        return RecommendationItemResponse.builder()
                .id(club.getId())
                .slug(club.getSlug())
                .type("CLUB")
                .title(club.getName())
                .subtitle("Câu lạc bộ phù hợp với hồ sơ của bạn")
                .imageUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .location(resolveLocation(club.getFacility(), club.getLocation()))
                .detailUrl("/clubs/" + club.getSlug())
                .status(club.getStatus().name())
                .facility(toFacilityResponse(club.getFacility()))
                .score(score.value())
                .distanceKm(distanceToFacility(club.getFacility(), preference).orElse(null))
                .minLevel(club.getMinLevel())
                .maxLevel(club.getMaxLevel())
                .totalSlots(club.getMaxMembers())
                .tags(club.getTags())
                .reasons(limitReasons(reasons))
                .build();
    }

    private RecommendationItemResponse toEventRecommendation(ClubEvent event, UserPreference preference) {
        List<String> reasons = new ArrayList<>();
        Score score = new Score(16);

        score.add(levelScore(event.getMinLevel(), event.getMaxLevel(), preference.skillScore(), reasons));
        score.add(distanceScore(event.getFacility(), preference, reasons));
        score.add(timeScore(event.getStartTime(), preference, reasons));

        if (preference.joinedClubIds().contains(event.getClub().getId())) {
            score.add(14);
            addReason(reasons, "Bạn đang là thành viên CLB tổ chức");
        }

        if (!Collections.disjoint(event.getCategories(), preference.favoriteCategories())) {
            score.add(10);
            addReason(reasons, "Trùng nội dung thi đấu bạn hay tham gia");
        }

        int joinedSlots = countJoinedEventSlots(event);
        if (event.getTotalMember() > 0 && joinedSlots < event.getTotalMember()) {
            score.add(6);
            addReason(reasons, "Hoạt động vẫn còn chỗ đăng ký");
        }

        return RecommendationItemResponse.builder()
                .id(event.getId())
                .slug(event.getSlug())
                .type("CLUB_EVENT")
                .title(event.getTitle())
                .subtitle("Hoạt động phù hợp để tham gia sớm")
                .imageUrl(fileStorageService.getFileUrl(event.getImage(), "/club/events"))
                .location(resolveLocation(event.getFacility(), event.getLocation()))
                .detailUrl("/events/" + event.getSlug())
                .clubName(event.getClub().getName())
                .status(event.getStatus().name())
                .facility(toFacilityResponse(event.getFacility()))
                .score(score.value())
                .distanceKm(distanceToFacility(event.getFacility(), preference).orElse(null))
                .minLevel(event.getMinLevel())
                .maxLevel(event.getMaxLevel())
                .totalSlots(event.getTotalMember())
                .joinedSlots(joinedSlots)
                .fee(event.getFee())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .categories(toCategoryNames(event.getCategories()))
                .reasons(limitReasons(reasons))
                .build();
    }

    private RecommendationItemResponse toTournamentRecommendation(Tournament tournament, UserPreference preference) {
        List<String> reasons = new ArrayList<>();
        Score score = new Score(14);

        score.add(distanceScore(tournament.getFacility(), preference, reasons));
        score.add(tournamentLevelScore(tournament.getCategories(), preference.skillScore(), reasons));
        score.add(tournamentCategoryScore(tournament.getCategories(), preference.favoriteCategories(), reasons));
        score.add(timeScore(tournament.getStartDate(), preference, reasons) / 2);

        if (tournament.getStatus() == TournamentStatus.REGISTRATION_OPEN) {
            score.add(12);
            addReason(reasons, "Giải đang mở đăng ký");
        } else if (tournament.getRegistrationEndDate() != null && tournament.getRegistrationEndDate().isAfter(LocalDateTime.now())) {
            score.add(8);
            addReason(reasons, "Vẫn còn thời gian đăng ký");
        }

        return RecommendationItemResponse.builder()
                .id(tournament.getId())
                .slug(tournament.getSlug())
                .type("TOURNAMENT")
                .title(tournament.getName())
                .subtitle("Giải đấu nên cân nhắc đăng ký")
                .imageUrl(fileStorageService.getFileUrl(
                        tournament.getBannerUrl() != null ? tournament.getBannerUrl() : tournament.getLogoUrl(),
                        "/tournament"
                ))
                .location(resolveLocation(tournament.getFacility(), tournament.getLocation()))
                .detailUrl("/tournaments/" + tournament.getSlug())
                .status(tournament.getStatus().name())
                .facility(toFacilityResponse(tournament.getFacility()))
                .score(score.value())
                .distanceKm(distanceToFacility(tournament.getFacility(), preference).orElse(null))
                .fee(tournament.getFee())
                .startTime(tournament.getStartDate())
                .endTime(tournament.getEndDate())
                .registrationEndDate(tournament.getRegistrationEndDate())
                .categories(toCategoryNames(tournament.getCategories()))
                .reasons(limitReasons(reasons))
                .build();
    }

    private UserPreference buildPreference(
            Account account,
            PlayerRating rating,
            List<UserSchedule> schedules,
            List<ClubMember> memberships,
            List<ClubEventParticipant> eventHistory,
            List<TournamentParticipant> tournamentHistory
    ) {
        double skillScore = rating != null ? rating.getOverallScore() : 2.5;
        UserInfo userInfo = account.getUserInfo();
        Double latitude = userInfo != null ? userInfo.getLatitude() : null;
        Double longitude = userInfo != null ? userInfo.getLongitude() : null;

        Set<String> joinedClubIds = memberships.stream()
                .map(member -> member.getClub().getId())
                .collect(Collectors.toSet());

        Set<String> favoriteTags = memberships.stream()
                .map(ClubMember::getClub)
                .filter(Objects::nonNull)
                .flatMap(club -> club.getTags().stream())
                .filter(Objects::nonNull)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toSet());

        Set<BadmintonCategoryEnum> favoriteCategories = new HashSet<>();
        eventHistory.stream()
                .map(ClubEventParticipant::getClubEvent)
                .filter(Objects::nonNull)
                .flatMap(event -> event.getCategories().stream())
                .forEach(favoriteCategories::add);
        tournamentHistory.stream()
                .map(TournamentParticipant::getCategory)
                .filter(Objects::nonNull)
                .map(TournamentCategory::getCategory)
                .filter(Objects::nonNull)
                .forEach(favoriteCategories::add);

        List<UserSchedule> activeSchedules = schedules.stream()
                .filter(this::isActiveSchedule)
                .toList();

        Set<DayOfWeek> preferredDays = activeSchedules.stream()
                .map(UserSchedule::getStartTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::getDayOfWeek)
                .collect(Collectors.toSet());

        Set<String> preferredTimeSlots = activeSchedules.stream()
                .map(UserSchedule::getStartTime)
                .filter(Objects::nonNull)
                .map(start -> timeSlotName(start.getHour()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new UserPreference(
                skillScore,
                latitude,
                longitude,
                activeSchedules,
                joinedClubIds,
                favoriteTags,
                favoriteCategories,
                preferredDays,
                preferredTimeSlots
        );
    }

    private RecommendationProfileResponse buildProfile(Account account, PlayerRating rating, UserPreference preference) {
        UserInfo userInfo = account.getUserInfo();
        return RecommendationProfileResponse.builder()
                .fullName(userInfo != null ? userInfo.getFullName() : account.getEmail())
                .skillScore(round1(preference.skillScore()))
                .skillLevel(rating != null ? rating.getSkillLevel() : "Chưa đánh giá")
                .hasLocation(preference.latitude() != null && preference.longitude() != null)
                .favoriteCategories(preference.favoriteCategories().stream()
                        .map(BadmintonCategoryEnum::name)
                        .sorted()
                        .toList())
                .preferredTimeSlots(new ArrayList<>(preference.preferredTimeSlots()))
                .build();
    }

    private double levelScore(double minLevel, double maxLevel, double userLevel, List<String> reasons) {
        if (maxLevel <= 0) {
            return 8;
        }
        if (userLevel >= minLevel && userLevel <= maxLevel) {
            addReason(reasons, "Phù hợp trình độ hiện tại của bạn");
            return 22;
        }

        double gap = userLevel < minLevel ? minLevel - userLevel : userLevel - maxLevel;
        if (gap <= 0.5) {
            addReason(reasons, "Gần với trình độ hiện tại của bạn");
            return 12;
        }
        return 2;
    }

    private double tournamentLevelScore(List<TournamentCategory> categories, double userLevel, List<String> reasons) {
        if (categories == null || categories.isEmpty()) {
            return 8;
        }

        double best = 0;
        for (TournamentCategory category : categories) {
            double min = category.getMinLevel() != null ? category.getMinLevel() : 0;
            double max = category.getMaxLevel() != null ? category.getMaxLevel() : 0;
            best = Math.max(best, levelScore(min, max, userLevel, reasons));
        }
        return best;
    }

    private double tournamentCategoryScore(
            List<TournamentCategory> categories,
            Set<BadmintonCategoryEnum> favoriteCategories,
            List<String> reasons
    ) {
        if (categories == null || categories.isEmpty() || favoriteCategories.isEmpty()) {
            return 0;
        }

        boolean matched = categories.stream()
                .map(TournamentCategory::getCategory)
                .anyMatch(favoriteCategories::contains);

        if (matched) {
            addReason(reasons, "Có hạng mục giống lịch sử thi đấu của bạn");
            return 12;
        }
        return 0;
    }

    private double distanceScore(Facility facility, UserPreference preference, List<String> reasons) {
        Optional<Double> distance = distanceToFacility(facility, preference);
        if (distance.isEmpty()) {
            return 6;
        }

        double distanceKm = distance.get();
        addReason(reasons, "Cách bạn khoảng " + GeoDistanceUtil.round2(distanceKm) + " km");
        if (distanceKm <= 3) return 24;
        if (distanceKm <= 7) return 20;
        if (distanceKm <= 15) return 14;
        if (distanceKm <= 30) return 8;
        return 3;
    }

    private double timeScore(LocalDateTime startTime, UserPreference preference, List<String> reasons) {
        if (startTime == null) {
            return 0;
        }

        double score = 0;
        if (preference.preferredDays().contains(startTime.getDayOfWeek())) {
            score += 6;
            addReason(reasons, "Trùng ngày bạn thường tham gia");
        }

        String slot = timeSlotName(startTime.getHour());
        if (preference.preferredTimeSlots().contains(slot)) {
            score += 6;
            addReason(reasons, "Khung giờ quen thuộc với lịch của bạn");
        }
        return score;
    }

    private boolean hasScheduleConflict(List<UserSchedule> schedules, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }

        return schedules.stream()
                .filter(this::isActiveSchedule)
                .anyMatch(schedule -> schedule.getStartTime() != null
                        && schedule.getEndTime() != null
                        && schedule.getStartTime().isBefore(endTime)
                        && schedule.getEndTime().isAfter(startTime));
    }

    private boolean isActiveSchedule(UserSchedule schedule) {
        return schedule.getStatus() != StatusScheduleEnum.CANCELLED
                && schedule.getStatus() != StatusScheduleEnum.REJECTED;
    }

    private boolean hasJoinedTournament(Account account, Tournament tournament) {
        if (tournament.getCategories() == null) {
            return false;
        }

        return tournament.getCategories().stream()
                .anyMatch(category -> tournamentParticipantRepository.existsByAccountAndCategory(account, category)
                        || tournamentTeamRepository.existsByAccountAndCategory(category.getId(), account));
    }

    private boolean hasSharedTags(Set<String> itemTags, Set<String> favoriteTags) {
        if (itemTags == null || itemTags.isEmpty() || favoriteTags.isEmpty()) {
            return false;
        }

        return itemTags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .anyMatch(favoriteTags::contains);
    }

    private int countJoinedEventSlots(ClubEvent event) {
        return (int) event.getParticipants().stream()
                .filter(participant -> participant.getStatus() != ClubEventParticipantStatusEnum.PENDING
                        && participant.getStatus() != ClubEventParticipantStatusEnum.CANCELLED)
                .count();
    }

    private Optional<Double> distanceToFacility(Facility facility, UserPreference preference) {
        if (facility == null
                || facility.getLatitude() == null
                || facility.getLongitude() == null
                || preference.latitude() == null
                || preference.longitude() == null) {
            return Optional.empty();
        }

        return Optional.of(GeoDistanceUtil.round2(GeoDistanceUtil.calculateDistanceKm(
                preference.latitude(),
                preference.longitude(),
                facility.getLatitude(),
                facility.getLongitude()
        )));
    }

    private String resolveLocation(Facility facility, String fallbackLocation) {
        if (facility != null) {
            return StreamLocation.of(facility);
        }
        return fallbackLocation;
    }

    private FacilityResponse toFacilityResponse(Facility facility) {
        if (facility == null) {
            return null;
        }
        return FacilityResponse.builder()
                .id(facility.getId())
                .name(facility.getName())
                .address(facility.getAddress())
                .district(facility.getDistrict())
                .city(facility.getCity())
                .location(facility.getLocation())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .image(fileStorageService.getFileUrl(facility.getImage(), "/facility"))
                .build();
    }

    private List<String> toCategoryNames(Collection<BadmintonCategoryEnum> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .map(BadmintonCategoryEnum::name)
                .sorted()
                .toList();
    }

    private List<String> toCategoryNames(List<TournamentCategory> categories) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .map(TournamentCategory::getCategory)
                .filter(Objects::nonNull)
                .map(BadmintonCategoryEnum::name)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> limitReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return List.of("Được chọn từ hồ sơ, lịch sử tham gia và dữ liệu gần đây");
        }
        return reasons.stream()
                .distinct()
                .limit(4)
                .toList();
    }

    private void addReason(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    private String timeSlotName(int hour) {
        if (hour < 11) return "Sáng";
        if (hour < 17) return "Chiều";
        return "Tối";
    }

    private int normalizeTop(int top) {
        if (top <= 0) {
            return DEFAULT_TOP;
        }
        return Math.min(top, MAX_TOP);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Account resolveCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
    }

    private record UserPreference(
            double skillScore,
            Double latitude,
            Double longitude,
            List<UserSchedule> schedules,
            Set<String> joinedClubIds,
            Set<String> favoriteTags,
            Set<BadmintonCategoryEnum> favoriteCategories,
            Set<DayOfWeek> preferredDays,
            Set<String> preferredTimeSlots
    ) {
    }

    private static class Score {
        private double value;

        Score(double initialValue) {
            this.value = initialValue;
        }

        void add(double amount) {
            value += amount;
        }

        double value() {
            return Math.max(0, Math.min(100, Math.round(value * 10.0) / 10.0));
        }
    }

    private static class StreamLocation {
        static String of(Facility facility) {
            return java.util.stream.Stream.of(facility.getName(), facility.getDistrict(), facility.getCity())
                    .filter(Objects::nonNull)
                    .filter(part -> !part.isBlank())
                    .collect(Collectors.joining(", "));
        }
    }
}
