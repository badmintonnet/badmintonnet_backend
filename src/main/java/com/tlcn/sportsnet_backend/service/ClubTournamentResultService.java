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

        // Hiển thị tên CLB / logo cho key match & ranking: gồm cả ELIMINATED (chỉ APPROVED
        // thì CLB thua không còn trong map → thấy 1 bên hoặc "Không xác định").
        List<ClubTournamentParticipant> forDisplayLabels = clubTournamentParticipantRepository
                .findByTournamentId(tournamentId)
                .stream()
                .filter(p -> p.getStatus() != ClubTournamentParticipantStatusEnum.CANCELLED
                        && p.getStatus() != ClubTournamentParticipantStatusEnum.REJECTED)
                .collect(Collectors.toList());

        Map<String, RepInfo> repMap = buildRepMap(forDisplayLabels);

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

        DuelSummary finalDuel = soleDuelAtRound(matches, maxRound).orElse(null);
        boolean finished =
                finalDuel != null && duelHasWinner(finalDuel.rubbers());

        List<ClubResultPodiumItem> podium =
                buildPodium(category, matches, maxRound, repMap, finished, finalDuel);
        List<ClubResultPodiumItem> ranking =
                buildRanking(matches, maxRound, repMap, podium);
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
            Integer maxRound,
            Map<String, RepInfo> repMap,
            boolean finished,
            DuelSummary finalDuel
    ) {
        if (!finished || finalDuel == null || finalDuel.rubbers().isEmpty()) {
            return List.of();
        }

        String championId = computeClubTieWinner(finalDuel.rubbers());
        if (championId == null) {
            return List.of();
        }

        TournamentMatch finalRef = finalDuel.rubbers().get(0);
        String runnerUpId = otherParticipantId(finalRef, championId);

        List<ClubResultPodiumItem> podium = new ArrayList<>();

        podium.add(toPodiumItem(championId, 1, category.getFirstPrize(), repMap));

        if (runnerUpId != null) {
            podium.add(toPodiumItem(runnerUpId, 2, category.getSecondPrize(), repMap));
        }

        if (maxRound != null && maxRound > 1) {
            int semiRound = maxRound - 1;
            for (DuelSummary semi : duelsAtRound(matches, semiRound)) {
                if (!duelHasWinner(semi.rubbers())) continue;
                String duelWinner = computeClubTieWinner(semi.rubbers());
                if (duelWinner == null) continue;
                TournamentMatch ref = semi.rubbers().get(0);
                String loserId = otherParticipantId(ref, duelWinner);
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
        Map<String, List<TournamentMatch>> duelsFlat = groupedClubDuels(matches);
        for (List<TournamentMatch> duelRubbers : duelsFlat.values()) {
            int round = duelRubbers.get(0).getRound();
            if (!duelHasWinner(duelRubbers)) continue;
            String winnerId = computeClubTieWinner(duelRubbers);
            if (winnerId == null) continue;
            TournamentMatch ref = duelRubbers.get(0);
            String loserId = otherParticipantId(ref, winnerId);
            if (loserId == null) continue;
            eliminationRound.merge(loserId, round, Math::max);
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

        soleDuelAtRound(matches, maxRound)
                .filter(d -> duelHasWinner(d.rubbers()))
                .ifPresent(
                        d ->
                                keyMatches.add(
                                        toMatchSummary(
                                                representativeRubber(d.rubbers()),
                                                "Chung kết",
                                                maxRound,
                                                repMap)));

        if (maxRound > 1) {
            int semiRound = maxRound - 1;
            duelsAtRound(matches, semiRound).stream()
                    .filter(d -> duelHasWinner(d.rubbers()))
                    .sorted(
                            Comparator.comparingInt(
                                    d -> d.rubbers().get(0).getMatchIndex()))
                    .forEach(
                            d ->
                                    keyMatches.add(
                                            toMatchSummary(
                                                    representativeRubber(d.rubbers()),
                                                    "Bán kết",
                                                    maxRound,
                                                    repMap)));
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

        Map<String, List<TournamentMatch>> duelsFlat = groupedClubDuels(matches);
        for (List<TournamentMatch> duelRubbers : duelsFlat.values()) {
            if (!duelHasWinner(duelRubbers)) continue;
            TournamentMatch ref = duelRubbers.get(0);
            String p1 = ref.getParticipant1Id();
            String p2 = ref.getParticipant2Id();
            if (p1 == null || p2 == null) continue;

            String duelWinner = computeClubTieWinner(duelRubbers);
            if (duelWinner == null) continue;

            int p1SetWins = 0, p2SetWins = 0;
            for (TournamentMatch m : duelRubbers) {
                if (m.getStatus() != MatchStatus.FINISHED || m.getWinnerId() == null)
                    continue;

                int setsP1 = m.getSetScoreP1() != null ? m.getSetScoreP1().size() : 0;
                int setsP2 = m.getSetScoreP2() != null ? m.getSetScoreP2().size() : 0;
                for (int i = 0; i < Math.max(setsP1, setsP2); i++) {
                    Integer s1 = i < setsP1 ? m.getSetScoreP1().get(i) : null;
                    Integer s2 = i < setsP2 ? m.getSetScoreP2().get(i) : null;
                    if (s1 == null || s2 == null) continue;
                    if (s1 > s2) p1SetWins++;
                    else if (s2 > s1) p2SetWins++;
                }
            }

            boolean p1WonTie = p1.equals(duelWinner);
            updateStat(statsMap, p1, p1WonTie, p1SetWins, p2SetWins);
            updateStat(statsMap, p2, !p1WonTie, p2SetWins, p1SetWins);
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

    /** Một duel CLB-vs-CLB = nhiều rubber (tieId giống nhau) hoặc một match legacy. */
    private record DuelSummary(String key, List<TournamentMatch> rubbers) {}

    private static final Comparator<TournamentMatch> RUBBER_ORDER =
            Comparator.comparing((TournamentMatch m) ->
                            m.getMatchIndex() == null ? 0 : m.getMatchIndex())
                    .thenComparing(m -> m.getLineType() == null ? "" : m.getLineType().name())
                    .thenComparingInt(m -> m.getLineIndex() == null ? 0 : m.getLineIndex());

    private Map<String, List<TournamentMatch>> groupedClubDuels(List<TournamentMatch> matches) {
        Map<String, List<TournamentMatch>> map = new LinkedHashMap<>();
        for (TournamentMatch m : matches) {
            String key =
                    m.getTieId() != null ? "T:" + m.getTieId() : "M:" + m.getId();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        for (List<TournamentMatch> list : map.values()) {
            list.sort(RUBBER_ORDER);
        }
        return map;
    }

    private List<DuelSummary> duelsAtRound(List<TournamentMatch> matches, int roundNum) {
        Map<String, List<TournamentMatch>> acc = new LinkedHashMap<>();
        for (TournamentMatch m : matches) {
            if (!Objects.equals(m.getRound(), roundNum)) continue;
            String key =
                    m.getTieId() != null ? "T:" + m.getTieId() : "M:" + m.getId();
            acc.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        List<DuelSummary> out = new ArrayList<>();
        for (Map.Entry<String, List<TournamentMatch>> e : acc.entrySet()) {
            List<TournamentMatch> sorted = new ArrayList<>(e.getValue());
            sorted.sort(RUBBER_ORDER);
            out.add(new DuelSummary(e.getKey(), sorted));
        }
        out.sort(
                Comparator.comparingInt(
                        d -> d.rubbers().getFirst().getMatchIndex() == null
                                ? 0
                                : d.rubbers().getFirst().getMatchIndex()));
        return out;
    }

    private Optional<DuelSummary> soleDuelAtRound(List<TournamentMatch> matches, Integer roundNum) {
        if (roundNum == null || roundNum <= 0) return Optional.empty();
        List<DuelSummary> ds = duelsAtRound(matches, roundNum);
        if (ds.size() != 1) return Optional.empty();
        return Optional.of(ds.get(0));
    }

    private TournamentMatch representativeRubber(List<TournamentMatch> rubbers) {
        return rubbers.stream()
                .filter(
                        m ->
                                m.getStatus() == MatchStatus.FINISHED
                                        && m.getWinnerId() != null)
                .max(RUBBER_ORDER)
                .orElse(rubbers.getFirst());
    }

    private boolean duelHasWinner(List<TournamentMatch> rubbers) {
        return computeClubTieWinner(rubbers) != null;
    }

    /**
     * Logic trùng với {@link com.tlcn.sportsnet_backend.service.TournamentBracketService} khi đóng
     * một tie CLB.
     */
    private static String computeClubTieWinner(List<TournamentMatch> rubbers) {
        if (rubbers == null || rubbers.isEmpty()) return null;
        TournamentMatch first = rubbers.getFirst();

        // Legacy CLUB (không có tieId): một hàng là cả trận
        if (first.getTieId() == null) {
            TournamentMatch m = rubbers.getFirst();
            if (m.getStatus() != MatchStatus.FINISHED || m.getWinnerId() == null) return null;
            return m.getWinnerId();
        }

        String club1Id = first.getParticipant1Id();
        String club2Id = first.getParticipant2Id();
        if (club1Id == null || club2Id == null) return null;

        int totalRubbers = rubbers.size();
        int majorityThreshold = (totalRubbers / 2) + 1;

        int club1Wins = 0, club2Wins = 0;
        int club1Sets = 0, club2Sets = 0;
        int finishedCount = 0;
        for (TournamentMatch m : rubbers) {
            if (m.getStatus() == MatchStatus.FINISHED) {
                finishedCount++;
                if (club1Id.equals(m.getWinnerId())) club1Wins++;
                else if (club2Id.equals(m.getWinnerId())) club2Wins++;
                List<Integer> s1 = m.getSetScoreP1();
                List<Integer> s2 = m.getSetScoreP2();
                int len = Math.min(s1 == null ? 0 : s1.size(), s2 == null ? 0 : s2.size());
                for (int i = 0; i < len; i++) {
                    if (s1.get(i) > s2.get(i)) club1Sets++;
                    else if (s2.get(i) > s1.get(i)) club2Sets++;
                }
            }
        }

        if (club1Wins >= majorityThreshold) return club1Id;
        if (club2Wins >= majorityThreshold) return club2Id;
        if (finishedCount == totalRubbers) {
            if (club1Sets > club2Sets) return club1Id;
            if (club2Sets > club1Sets) return club2Id;
        }
        return null;
    }

    private Map<String, RepInfo> buildRepMap(List<ClubTournamentParticipant> approved) {
        Map<String, RepInfo> repMap = new LinkedHashMap<>();
        for (ClubTournamentParticipant p : approved) {
            Club club = p.getClub();
            String pid = p.getId();

            Optional<ClubTournamentRoster> repOpt =
                    clubTournamentRosterRepository
                            .findByClubTournamentParticipant_IdAndPositionWithDetails(
                                    pid, "SINGLES_1");
            if (repOpt.isEmpty()) {
                repOpt =
                        clubTournamentRosterRepository
                                .findByClubTournamentParticipant_IdAndPositionWithDetails(
                                        pid, "SINGLES");
            }

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
