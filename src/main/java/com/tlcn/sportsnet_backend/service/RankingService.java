package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.ranking.PlayerRankingResponse;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubMember;
import com.tlcn.sportsnet_backend.entity.PlayerTournamentHistory;
import com.tlcn.sportsnet_backend.entity.TournamentMatch;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.enums.MatchStatus;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.ClubMemberRepository;
import com.tlcn.sportsnet_backend.repository.ClubRepository;
import com.tlcn.sportsnet_backend.repository.PlayerTournamentHistoryRepository;
import com.tlcn.sportsnet_backend.repository.TournamentCategoryRepository;
import com.tlcn.sportsnet_backend.repository.TournamentMatchRepository;
import com.tlcn.sportsnet_backend.repository.TournamentParticipantRepository;
import com.tlcn.sportsnet_backend.repository.TournamentTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingService {
    private static final double TOURNAMENT_WEIGHT = 0.4;
    private static final double WIN_RATE_WEIGHT = 0.2;
    private static final double MATCH_VOLUME_WEIGHT = 0.2;
    private static final double REPUTATION_WEIGHT = 0.2;
    private static final int MATCH_VOLUME_CAP = 30;
    private static final int MIN_MATCHES_FOR_STABLE_RANK = 3;

    private final AccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final PlayerTournamentHistoryRepository historyRepository;
    private final TournamentCategoryRepository categoryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentTeamRepository teamRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PagedResponse<PlayerRankingResponse> getRankings(
            String scope,
            String area,
            String province,
            String ward,
            String club,
            int page,
            int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);
        String normalizedScope = scope == null || scope.isBlank()
                ? "GLOBAL"
                : scope.trim().toUpperCase(Locale.ROOT);

        List<Account> accounts = resolveCandidates(normalizedScope, area, province, ward, club);
        List<PlayerRankingResponse> rankedPlayers = accounts.stream()
                .filter(Account::isEnabled)
                .map(this::toRankingResponse)
                .sorted(rankingComparator())
                .toList();

        for (int index = 0; index < rankedPlayers.size(); index++) {
            rankedPlayers.get(index).setRank(index + 1);
        }

        int totalElements = rankedPlayers.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);
        int fromIndex = (int) Math.min((long) normalizedPage * normalizedSize, totalElements);
        int toIndex = Math.min(fromIndex + normalizedSize, totalElements);

        return new PagedResponse<>(
                rankedPlayers.subList(fromIndex, toIndex),
                normalizedPage,
                normalizedSize,
                totalElements,
                totalPages,
                toIndex >= totalElements
        );
    }

    private List<Account> resolveCandidates(String scope, String area, String province, String ward, String club) {
        return switch (scope) {
            case "GLOBAL" -> accountRepository.findAllWithUserInfo();
            case "AREA" -> findAccountsByArea(area, province, ward);
            case "CLUB" -> findAccountsByClub(club);
            default -> throw new InvalidDataException("Scope bang xep hang khong hop le");
        };
    }

    private List<Account> findAccountsByArea(String area, String province, String ward) {
        String normalizedProvince = normalizeForSearch(province);
        String normalizedWard = normalizeForSearch(ward);
        String normalizedArea = normalizedProvince.isBlank() && normalizedWard.isBlank()
                ? normalizeForSearch(area)
                : "";

        if (normalizedProvince.isBlank() && normalizedWard.isBlank() && normalizedArea.isBlank()) {
            return accountRepository.findAllWithUserInfo();
        }

        return accountRepository.findAllWithUserInfo().stream()
                .filter(account -> account.getUserInfo() != null)
                .filter(account -> matchesAddress(account, normalizedArea, normalizedProvince, normalizedWard))
                .toList();
    }

    private boolean matchesAddress(Account account, String area, String province, String ward) {
        String normalizedAddress = normalizeForSearch(account.getUserInfo().getAddress());

        if (!province.isBlank() && !normalizedAddress.contains(province)) {
            return false;
        }

        if (!ward.isBlank() && !normalizedAddress.contains(ward)) {
            return false;
        }

        return area.isBlank() || normalizedAddress.contains(area);
    }

    private List<Account> findAccountsByClub(String clubIdOrSlug) {
        if (clubIdOrSlug == null || clubIdOrSlug.isBlank()) {
            throw new InvalidDataException("Vui long chon cau lac bo de xem bang xep hang");
        }

        Club resolvedClub = clubRepository.findById(clubIdOrSlug)
                .or(() -> clubRepository.findBySlug(clubIdOrSlug))
                .orElseThrow(() -> new InvalidDataException("Khong tim thay cau lac bo"));

        return distinctAccounts(clubMemberRepository
                .findByClubIdAndStatusWithAccount(resolvedClub.getId(), ClubMemberStatusEnum.APPROVED)
                .stream()
                .map(ClubMember::getAccount)
                .toList());
    }

    private PlayerRankingResponse toRankingResponse(Account account) {
        PlayerRankingStats stats = calculateStats(account);
        double reputationScore = clamp(account.getReputationScore(), 0, 100);
        double competitiveScore = TOURNAMENT_WEIGHT * stats.tournamentScore()
                + WIN_RATE_WEIGHT * stats.winRateScore()
                + MATCH_VOLUME_WEIGHT * stats.matchVolumeScore()
                + REPUTATION_WEIGHT * reputationScore;

        return PlayerRankingResponse.builder()
                .accountId(account.getId())
                .slug(account.getUserInfo() == null ? null : account.getUserInfo().getSlug())
                .fullName(account.getUserInfo() == null ? account.getEmail() : account.getUserInfo().getFullName())
                .avatarUrl(account.getUserInfo() == null
                        ? null
                        : fileStorageService.getFileUrl(account.getUserInfo().getAvatarUrl(), "/avatar"))
                .address(account.getUserInfo() == null ? null : account.getUserInfo().getAddress())
                .competitiveScore(roundTwoDecimals(competitiveScore))
                .tournamentScore(roundTwoDecimals(stats.tournamentScore()))
                .winRateScore(roundTwoDecimals(stats.winRateScore()))
                .matchVolumeScore(roundTwoDecimals(stats.matchVolumeScore()))
                .reputationScore((int) reputationScore)
                .totalMatches(stats.totalMatches())
                .totalWins(stats.totalWins())
                .winRate(roundTwoDecimals(stats.winRate()))
                .completedTournaments(stats.completedTournaments())
                .provisional(stats.totalMatches() < MIN_MATCHES_FOR_STABLE_RANK && stats.completedTournaments() == 0)
                .build();
    }

    private PlayerRankingStats calculateStats(Account account) {
        List<PlayerTournamentHistory> histories = historyRepository.findByPlayerIdOrderByCreatedAtDesc(account.getId());

        int totalMatches = 0;
        int totalWins = 0;
        int completedTournaments = 0;
        double tournamentScoreSum = 0;

        for (PlayerTournamentHistory history : histories) {
            if (history.getFinalRanking() != null) {
                Optional<Double> placementScore = calculatePlacementScore(history);
                if (placementScore.isPresent()) {
                    tournamentScoreSum += placementScore.get();
                    completedTournaments++;
                }
            }

            MatchCount matchCount = countFinishedMatches(account, history);
            totalMatches += matchCount.totalMatches();
            totalWins += matchCount.totalWins();
        }

        double tournamentScore = completedTournaments == 0 ? 0 : tournamentScoreSum / completedTournaments;
        double winRate = totalMatches == 0 ? 0 : (double) totalWins / totalMatches * 100;
        double matchVolumeScore = calculateMatchVolumeScore(totalMatches);

        return new PlayerRankingStats(
                totalMatches,
                totalWins,
                completedTournaments,
                tournamentScore,
                winRate,
                matchVolumeScore
        );
    }

    private Optional<Double> calculatePlacementScore(PlayerTournamentHistory history) {
        if (history.getCategoryId() == null || history.getFinalRanking() == null) {
            return Optional.empty();
        }

        return categoryRepository.findById(history.getCategoryId())
                .map(category -> {
                    int participantCount = history.isDouble()
                            ? teamRepository.countByCategory(category)
                            : participantRepository.countByCategory(category);

                    if (participantCount <= 1) {
                        return history.getFinalRanking() == 1 ? 100D : 0D;
                    }

                    double rawScore = (1D - ((double) history.getFinalRanking() - 1D) / (participantCount - 1D)) * 100D;
                    return clamp(rawScore, 0, 100);
                });
    }

    private MatchCount countFinishedMatches(Account account, PlayerTournamentHistory history) {
        if (history.getCategoryId() == null) {
            return new MatchCount(0, 0);
        }

        Optional<String> entrantId = resolveEntrantId(account, history);
        if (entrantId.isEmpty()) {
            return new MatchCount(0, 0);
        }

        List<TournamentMatch> matches = matchRepository.findByCategoryIdAndEntrantIdAndStatus(
                history.getCategoryId(),
                entrantId.get(),
                MatchStatus.FINISHED
        );

        int wins = (int) matches.stream()
                .filter(match -> entrantId.get().equals(match.getWinnerId()))
                .count();

        return new MatchCount(matches.size(), wins);
    }

    private Optional<String> resolveEntrantId(Account account, PlayerTournamentHistory history) {
        if (history.isDouble()) {
            return Optional.ofNullable(history.getTeamId());
        }

        return participantRepository
                .findByAccount_IdAndCategory_Id(account.getId(), history.getCategoryId())
                .map(participant -> participant.getId());
    }

    private double calculateMatchVolumeScore(int totalMatches) {
        if (totalMatches <= 0) {
            return 0;
        }
        return clamp(Math.log1p(totalMatches) / Math.log1p(MATCH_VOLUME_CAP) * 100D, 0, 100);
    }

    private Comparator<PlayerRankingResponse> rankingComparator() {
        return Comparator
                .comparingDouble(PlayerRankingResponse::getCompetitiveScore).reversed()
                .thenComparing(Comparator.comparingDouble(PlayerRankingResponse::getTournamentScore).reversed())
                .thenComparing(Comparator.comparingDouble(PlayerRankingResponse::getWinRate).reversed())
                .thenComparing(Comparator.comparingInt(PlayerRankingResponse::getReputationScore).reversed())
                .thenComparing(Comparator.comparingInt(PlayerRankingResponse::getTotalMatches).reversed())
                .thenComparing(PlayerRankingResponse::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private List<Account> distinctAccounts(List<Account> accounts) {
        Map<String, Account> distinct = new LinkedHashMap<>();
        for (Account account : accounts) {
            if (account != null) {
                distinct.put(account.getId(), account);
            }
        }
        return distinct.values().stream().toList();
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record PlayerRankingStats(
            int totalMatches,
            int totalWins,
            int completedTournaments,
            double tournamentScore,
            double winRateScore,
            double matchVolumeScore
    ) {
        double winRate() {
            return winRateScore;
        }
    }

    private record MatchCount(int totalMatches, int totalWins) {
    }
}
