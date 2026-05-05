package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.club_tournament.ClubMatchParticipantResponse;
import com.tlcn.sportsnet_backend.dto.club_tournament.result.*;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.MatchStatus;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubTournamentResultService {

    private final TournamentRepository tournamentRepository;
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final ClubTournamentParticipantRepository clubTournamentParticipantRepository;
    private final ClubTournamentRosterRepository clubTournamentRosterRepository;
    private final FileStorageService fileStorageService;

    public ClubTournamentResultResponse getResults(String tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy giải đấu"));

        if (tournament.getParticipationType() != TournamentParticipationTypeEnum.CLUB) {
            throw new InvalidDataException("Giải đấu không phải loại CLUB");
        }

        List<ClubTournamentParticipant> approved = clubTournamentParticipantRepository
                .findByTournamentIdAndStatus(tournamentId, ClubTournamentParticipantStatusEnum.APPROVED);

        Map<String, RepInfo> repMap = buildRepMap(approved);

        TournamentCategory category = tournamentCategoryRepository
                .findByTournamentIdAndCategory(tournamentId, BadmintonCategoryEnum.MEN_SINGLE)
                .orElse(null);

        if (category == null) {
            return ClubTournamentResultResponse.builder()
                    .tournamentId(tournament.getId())
                    .tournamentName(tournament.getName())
                    .status(tournament.getStatus())
                    .finished(false)
                    .totalClubs(approved.size())
                    .podium(List.of())
                    .ranking(buildEmptyRanking(repMap))
                    .keyMatches(List.of())
                    .clubStats(buildEmptyStats(repMap))
                    .build();
        }

        List<TournamentMatch> matches = tournamentMatchRepository.findByCategory(category);
        Integer maxRound = matches.stream()
                .map(TournamentMatch::getRound)
                .max(Integer::compareTo)
                .orElse(0);

        TournamentMatch finalMatch = matches.stream()
                .filter(m -> Objects.equals(m.getRound(), maxRound))
                .findFirst()
                .orElse(null);

        boolean finished = finalMatch != null
                && finalMatch.getStatus() == MatchStatus.FINISHED
                && finalMatch.getWinnerId() != null;

        List<ClubResultPodiumItem> podium = buildPodium(category, matches, finalMatch, maxRound, repMap, finished);
        List<ClubResultPodiumItem> ranking = buildRanking(matches, maxRound, repMap, podium);
        List<ClubResultMatchSummary> keyMatches = buildKeyMatches(matches, maxRound, repMap);
        List<ClubResultClubStat> clubStats = buildClubStats(matches, repMap);

        return ClubTournamentResultResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .status(tournament.getStatus())
                .finished(finished)
                .totalClubs(approved.size())
                .podium(podium)
                .ranking(ranking)
                .keyMatches(keyMatches)
                .clubStats(clubStats)
                .build();
    }

    // =========================================================
    // PODIUM
    // =========================================================

    private List<ClubResultPodiumItem> buildPodium(
            TournamentCategory category,
            List<TournamentMatch> matches,
            TournamentMatch finalMatch,
            Integer maxRound,
            Map<String, RepInfo> repMap,
            boolean finished
    ) {
        if (!finished || finalMatch == null) {
            return List.of();
        }

        List<ClubResultPodiumItem> podium = new ArrayList<>();

        // 1st
        podium.add(toPodiumItem(finalMatch.getWinnerId(), 1, category.getFirstPrize(), repMap));

        // 2nd
        String runnerUpId = otherParticipantId(finalMatch, finalMatch.getWinnerId());
        if (runnerUpId != null) {
            podium.add(toPodiumItem(runnerUpId, 2, category.getSecondPrize(), repMap));
        }

        // 3rd: losers of semi-finals
        if (maxRound != null && maxRound > 1) {
            int semiRound = maxRound - 1;
            List<TournamentMatch> semis = matches.stream()
                    .filter(m -> Objects.equals(m.getRound(), semiRound))
                    .toList();

            for (TournamentMatch semi : semis) {
                if (semi.getStatus() != MatchStatus.FINISHED || semi.getWinnerId() == null) continue;
                String loserId = otherParticipantId(semi, semi.getWinnerId());
                if (loserId != null) {
                    podium.add(toPodiumItem(loserId, 3, category.getThirdPrize(), repMap));
                }
            }
        }

        return podium;
    }

    private ClubResultPodiumItem toPodiumItem(
            String participantId,
            int ranking,
            String prize,
            Map<String, RepInfo> repMap
    ) {
        RepInfo info = repMap.get(participantId);
        if (info == null) {
            return ClubResultPodiumItem.builder()
                    .ranking(ranking)
                    .prize(prize)
                    .participantId(participantId)
                    .clubName("CLB không xác định")
                    .build();
        }
        return ClubResultPodiumItem.builder()
                .ranking(ranking)
                .prize(prize)
                .participantId(participantId)
                .clubId(info.clubId)
                .clubName(info.clubName)
                .clubLogoUrl(fileStorageService.getFileUrl(info.clubLogoUrl, "/club/logo"))
                .representativeName(info.memberName)
                .representativeAvatarUrl(fileStorageService.getFileUrl(info.memberAvatarUrl, "/avatar"))
                .build();
    }

    // =========================================================
    // FULL RANKING
    // =========================================================

    private List<ClubResultPodiumItem> buildRanking(
            List<TournamentMatch> matches,
            Integer maxRound,
            Map<String, RepInfo> repMap,
            List<ClubResultPodiumItem> podium
    ) {
        Set<String> ranked = podium.stream()
                .map(ClubResultPodiumItem::getParticipantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ClubResultPodiumItem> ranking = new ArrayList<>(podium);

        if (maxRound == null || maxRound == 0) {
            return ranking;
        }

        // Các CLB còn lại: sắp xếp theo round bị loại (round cao hơn = hạng cao hơn)
        // Round bị loại = round cuối mà CLB tham gia thua (hoặc có mặt nhưng không advance).
        Map<String, Integer> eliminationRound = new HashMap<>();
        for (TournamentMatch m : matches) {
            if (m.getStatus() != MatchStatus.FINISHED || m.getWinnerId() == null) continue;
            String loserId = otherParticipantId(m, m.getWinnerId());
            if (loserId == null) continue;
            eliminationRound.merge(loserId, m.getRound(), Math::max);
        }

        // Bắt đầu từ hạng 4 trở đi (đã có top 3)
        int nextRank = ranked.size() + 1;
        List<Map.Entry<String, Integer>> sorted = eliminationRound.entrySet().stream()
                .filter(e -> !ranked.contains(e.getKey()))
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        for (Map.Entry<String, Integer> entry : sorted) {
            ranking.add(toPodiumItem(entry.getKey(), nextRank++, null, repMap));
        }

        // Các CLB chưa từng đấu (vẫn còn ở bracket nhưng tournament dở dang)
        for (RepInfo info : repMap.values()) {
            if (ranked.contains(info.participantId)) continue;
            if (eliminationRound.containsKey(info.participantId)) continue;
            ranking.add(toPodiumItem(info.participantId, nextRank++, null, repMap));
        }

        return ranking;
    }

    private List<ClubResultPodiumItem> buildEmptyRanking(Map<String, RepInfo> repMap) {
        List<ClubResultPodiumItem> ranking = new ArrayList<>();
        int rank = 1;
        for (RepInfo info : repMap.values()) {
            ranking.add(toPodiumItem(info.participantId, rank++, null, repMap));
        }
        return ranking;
    }

    // =========================================================
    // KEY MATCHES (Final + Semis)
    // =========================================================

    private List<ClubResultMatchSummary> buildKeyMatches(
            List<TournamentMatch> matches,
            Integer maxRound,
            Map<String, RepInfo> repMap
    ) {
        if (maxRound == null || maxRound == 0) return List.of();

        List<ClubResultMatchSummary> keyMatches = new ArrayList<>();

        // Final
        matches.stream()
                .filter(m -> Objects.equals(m.getRound(), maxRound))
                .filter(m -> m.getStatus() == MatchStatus.FINISHED)
                .findFirst()
                .ifPresent(m -> keyMatches.add(toMatchSummary(m, "Chung kết", maxRound, repMap)));

        // Semi-finals
        if (maxRound > 1) {
            int semiRound = maxRound - 1;
            matches.stream()
                    .filter(m -> Objects.equals(m.getRound(), semiRound))
                    .filter(m -> m.getStatus() == MatchStatus.FINISHED)
                    .sorted(Comparator.comparingInt(TournamentMatch::getMatchIndex))
                    .forEach(m -> keyMatches.add(toMatchSummary(m, "Bán kết", maxRound, repMap)));
        }

        return keyMatches;
    }

    private ClubResultMatchSummary toMatchSummary(
            TournamentMatch m,
            String label,
            Integer maxRound,
            Map<String, RepInfo> repMap
    ) {
        return ClubResultMatchSummary.builder()
                .matchId(m.getId())
                .label(label)
                .round(m.getRound())
                .matchIndex(m.getMatchIndex())
                .player1(toMatchParticipant(m.getParticipant1Id(), repMap))
                .player2(toMatchParticipant(m.getParticipant2Id(), repMap))
                .setScoreP1(m.getSetScoreP1())
                .setScoreP2(m.getSetScoreP2())
                .winnerId(m.getWinnerId())
                .winnerName(m.getWinnerName())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .build();
    }

    private ClubMatchParticipantResponse toMatchParticipant(String participantId, Map<String, RepInfo> repMap) {
        if (participantId == null) return null;
        RepInfo info = repMap.get(participantId);
        if (info == null) return null;
        return ClubMatchParticipantResponse.builder()
                .participantId(info.participantId)
                .clubId(info.clubId)
                .clubName(info.clubName)
                .clubLogoUrl(fileStorageService.getFileUrl(info.clubLogoUrl, "/club/logo"))
                .memberId(info.accountId)
                .memberName(info.memberName)
                .memberAvatarUrl(fileStorageService.getFileUrl(info.memberAvatarUrl, "/avatar"))
                .build();
    }

    // =========================================================
    // CLUB STATS (W/L per club)
    // =========================================================

    private List<ClubResultClubStat> buildClubStats(
            List<TournamentMatch> matches,
            Map<String, RepInfo> repMap
    ) {
        Map<String, ClubResultClubStat> statsMap = new LinkedHashMap<>();

        // Khởi tạo stats cho tất cả participants
        for (RepInfo info : repMap.values()) {
            statsMap.put(info.participantId, ClubResultClubStat.builder()
                    .participantId(info.participantId)
                    .clubId(info.clubId)
                    .clubName(info.clubName)
                    .clubLogoUrl(fileStorageService.getFileUrl(info.clubLogoUrl, "/club/logo"))
                    .played(0)
                    .wins(0)
                    .losses(0)
                    .setsWon(0)
                    .setsLost(0)
                    .build());
        }

        for (TournamentMatch m : matches) {
            if (m.getStatus() != MatchStatus.FINISHED || m.getWinnerId() == null) continue;

            String p1 = m.getParticipant1Id();
            String p2 = m.getParticipant2Id();
            if (p1 == null || p2 == null) continue;

            int setsP1 = m.getSetScoreP1() != null ? m.getSetScoreP1().size() : 0;
            int setsP2 = m.getSetScoreP2() != null ? m.getSetScoreP2().size() : 0;

            int p1SetWins = 0;
            int p2SetWins = 0;
            for (int i = 0; i < Math.max(setsP1, setsP2); i++) {
                Integer s1 = i < setsP1 ? m.getSetScoreP1().get(i) : null;
                Integer s2 = i < setsP2 ? m.getSetScoreP2().get(i) : null;
                if (s1 == null || s2 == null) continue;
                if (s1 > s2) p1SetWins++;
                else if (s2 > s1) p2SetWins++;
            }

            updateStat(statsMap, p1, p1.equals(m.getWinnerId()), p1SetWins, p2SetWins);
            updateStat(statsMap, p2, p2.equals(m.getWinnerId()), p2SetWins, p1SetWins);
        }

        return new ArrayList<>(statsMap.values());
    }

    private void updateStat(
            Map<String, ClubResultClubStat> statsMap,
            String participantId,
            boolean won,
            int setsWon,
            int setsLost
    ) {
        ClubResultClubStat stat = statsMap.get(participantId);
        if (stat == null) return;
        stat.setPlayed(stat.getPlayed() + 1);
        if (won) {
            stat.setWins(stat.getWins() + 1);
        } else {
            stat.setLosses(stat.getLosses() + 1);
        }
        stat.setSetsWon(stat.getSetsWon() + setsWon);
        stat.setSetsLost(stat.getSetsLost() + setsLost);
    }

    private List<ClubResultClubStat> buildEmptyStats(Map<String, RepInfo> repMap) {
        return repMap.values().stream()
                .map(info -> ClubResultClubStat.builder()
                        .participantId(info.participantId)
                        .clubId(info.clubId)
                        .clubName(info.clubName)
                        .clubLogoUrl(fileStorageService.getFileUrl(info.clubLogoUrl, "/club/logo"))
                        .played(0)
                        .wins(0)
                        .losses(0)
                        .setsWon(0)
                        .setsLost(0)
                        .build())
                .toList();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Map<String, RepInfo> buildRepMap(List<ClubTournamentParticipant> approved) {
        Map<String, RepInfo> repMap = new LinkedHashMap<>();
        for (ClubTournamentParticipant p : approved) {
            Club club = p.getClub();
            String pid = p.getId();

            Optional<ClubTournamentRoster> repOpt = clubTournamentRosterRepository
                    .findByClubTournamentParticipant_IdAndPositionWithDetails(pid, "SINGLES");

            String accountId = null;
            String memberName = null;
            String memberAvatarUrl = null;

            if (repOpt.isPresent()) {
                ClubTournamentRoster r = repOpt.get();
                ClubMember cm = r.getClubMember();
                Account acc = cm.getAccount();
                accountId = acc.getId();
                memberName = acc.getUserInfo() != null ? acc.getUserInfo().getFullName() : null;
                memberAvatarUrl = acc.getUserInfo() != null ? acc.getUserInfo().getAvatarUrl() : null;
            }

            repMap.put(pid, new RepInfo(
                    pid,
                    club.getId(),
                    club.getName(),
                    club.getLogoUrl(),
                    accountId,
                    memberName,
                    memberAvatarUrl
            ));
        }
        return repMap;
    }

    private String otherParticipantId(TournamentMatch match, String knownId) {
        if (knownId == null) return null;
        if (knownId.equals(match.getParticipant1Id())) return match.getParticipant2Id();
        if (knownId.equals(match.getParticipant2Id())) return match.getParticipant1Id();
        return null;
    }

    // Internal record
    private record RepInfo(
            String participantId,
            String clubId,
            String clubName,
            String clubLogoUrl,
            String accountId,
            String memberName,
            String memberAvatarUrl
    ) {}
}
