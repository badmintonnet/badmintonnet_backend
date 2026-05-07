package com.tlcn.sportsnet_backend.service;


import ch.qos.logback.classic.Logger;
import com.tlcn.sportsnet_backend.dto.bracket.*;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.MatchStatus;
import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentBracketService {
    private final TournamentParticipantRepository participantRepo;
    private final TournamentTeamRepository teamRepo;
    private final TournamentMatchRepository matchRepo;
    private final TournamentCategoryRepository categoryRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerTournamentHistoryService historyService;
    private final TournamentResultService resultService;
    private final TournamentPaymentRepository paymentRepo;
    private final ClubTournamentParticipantRepository clubParticipantRepo;
    private final ClubTournamentRosterRepository clubRosterRepo;

    private static final Logger log =
            (Logger) LoggerFactory.getLogger(TournamentBracketService.class);

    public List<TournamentMatchResponse> generateBracket(String categoryId) {

        TournamentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        List<? extends BracketParticipant> participants;

        // loại participant
        String type = category.getCategory().getType();

        if (type.equals("SINGLE")) {

            List<TournamentParticipant> list =
                    participantRepo.findByCategory(category);

            if (list.isEmpty()) {
                throw new RuntimeException("Category has no participants");
            }

            boolean hasUnpaid = list.stream()
                    .anyMatch(p -> !paymentRepo.existsByParticipantAndStatus(
                            p, PaymentStatusEnum.SUCCESS));

            if (hasUnpaid) {
                throw new RuntimeException("Not all participants have completed payment");
            }

            participants = list;

        } else if (type.equals("DOUBLE")) {

            List<TournamentTeam> list =
                    teamRepo.findByCategory(category);

            if (list.isEmpty()) {
                throw new RuntimeException("Category has no teams");
            }
            boolean hasUnpaid = list.stream()
                    .anyMatch(t -> !paymentRepo.existsByTeamAndStatus(
                            t, PaymentStatusEnum.SUCCESS));

            if (hasUnpaid) {
                throw new RuntimeException("Not all teams have completed payment");
            }

            participants = list;

        } else {
            throw new RuntimeException("Invalid category type");
        }

        return generateBracketGeneric(category, participants);
    }


    private <T extends BracketParticipant> List<TournamentMatchResponse> generateBracketGeneric(
            TournamentCategory category,
            List<T> participants
    ) {

        int n = participants.size();
        int bracketSize = nextPowerOfTwo(n);

        List<T> list = new ArrayList<>(participants);

        while (list.size() < bracketSize) list.add(null);

        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        List<TournamentMatch> allMatches = new ArrayList<>();
        List<T> current = list;

        for (int round = 1; round <= totalRounds; round++) {

            List<T> nextRound = new ArrayList<>();
            int index = 1;

            for (int i = 0; i < current.size(); i += 2) {

                T p1 = current.get(i);
                T p2 = current.get(i + 1);

                TournamentMatch match = TournamentMatch.builder()
                        .category(category)
                        .round(round)
                        .matchIndex(index++)
                        .participant1Id(p1 != null ? p1.getId() : null)
                        .participant2Id(p2 != null ? p2.getId() : null)
                        .participant1Name(p1 != null ? p1.getDisplayName() : null)
                        .participant2Name(p2 != null ? p2.getDisplayName() : null)
                        .status(MatchStatus.NOT_STARTED)
                        .build();

                matchRepo.save(match);
                allMatches.add(match);

                nextRound.add(null);
            }

            current = nextRound;
        }

        return allMatches.stream()
                .map(this::convertToResponse)
                .toList();
    }

    private int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p *= 2;
        return p;
    }

    public TournamentMatchResponse updateMatchResult(String matchId, UpdateMatchResultRequest req) {

        TournamentMatch match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (req.getSets() == null || req.getSets().isEmpty()) {
            throw new InvalidDataException("Set scores are required");
        }

        int winP1 = 0;
        int winP2 = 0;

        List<Integer> setP1 = new ArrayList<>();
        List<Integer> setP2 = new ArrayList<>();

        for (SetScore s : req.getSets()) {
            int p1 = s.getP1();
            int p2 = s.getP2();

            setP1.add(p1);
            setP2.add(p2);

            int winner = getSetWinner(p1, p2);

            if (winner == 1) {
                winP1++;
            } else if (winner == 2) {
                winP2++;
            }

            // best of 3 → ai thắng 2 set thì dừng
            if (winP1 == 2 || winP2 == 2) {
                break;
            }
        }

        // cập nhật điểm (luôn cập nhật)
        match.setSetScoreP1(setP1);
        match.setSetScoreP2(setP2);

        boolean hasWinner = winP1 == 2 || winP2 == 2;

        if (hasWinner) {
            boolean p1Winner = winP1 > winP2;

            String winnerId = p1Winner
                    ? match.getParticipant1Id()
                    : match.getParticipant2Id();

            String winnerName = p1Winner
                    ? match.getParticipant1Name()
                    : match.getParticipant2Name();

            match.setWinnerId(winnerId);
            match.setWinnerName(winnerName);
            match.setStatus(MatchStatus.FINISHED);

            matchRepo.save(match);

            boolean isClub = match.getCategory().getTournament().getParticipationType()
                    == TournamentParticipationTypeEnum.CLUB;

            if (isClub && match.getTieId() != null) {
                // Tie-based CLUB tournament: kiểm tra trạng thái tie
                handleClubTieAfterRubber(match);
            } else if (isClub) {
                // Legacy CLUB (1 match/cặp, không có tieId)
                String loserId = p1Winner
                        ? match.getParticipant2Id()
                        : match.getParticipant1Id();
                if (loserId != null) {
                    clubParticipantRepo.findById(loserId).ifPresent(loser -> {
                        loser.setStatus(ClubTournamentParticipantStatusEnum.ELIMINATED);
                        clubParticipantRepo.save(loser);
                    });
                }
                advanceWinner(match);
            } else {
                advanceWinner(match);
            }

            try {
                historyService.finishMatch(match.getId());
            } catch (Exception e) {
                log.error("Error saving match history for matchId={}", match.getId(), e);
            }

            // TỰ ĐỘNG GENERATE KẾT QUẢ NẾU LÀ FINAL (chỉ cho INDIVIDUAL)
            Integer maxRound = matchRepo.findMaxRoundByCategory(match.getCategory());
            if (match.getRound().equals(maxRound) && !isClub) {
                resultService.generateResultForCategory(match.getCategory());
                historyService.updateHistoryFromCategoryResult(match.getCategory());
            }

        } else {
            match.setStatus(MatchStatus.IN_PROGRESS);
            matchRepo.save(match);
        }

        TournamentMatchResponse res = convertToResponse(match);

        // luôn bắn socket để FE cập nhật realtime
        messagingTemplate.convertAndSend(
                "/topic/match-updates/" + match.getCategory().getId(),
                res
        );

        return res;
    }

    private int getSetWinner(int p1, int p2) {
        // chưa kết thúc set
        if (p1 < 21 && p2 < 21) return 0;

        // trường hợp chạm 30
        if (p1 == 30 && p2 == 29) return 1;
        if (p2 == 30 && p1 == 29) return 2;

        // >=21 và hơn 2 điểm
        if (p1 >= 21 && p1 - p2 >= 2) return 1;
        if (p2 >= 21 && p2 - p1 >= 2) return 2;

        return 0; // set chưa hợp lệ / chưa kết thúc
    }


    private void advanceWinner(TournamentMatch match) {

        TournamentCategory category = match.getCategory();
        int nextRound = match.getRound() + 1;

        List<TournamentMatch> nextMatches =
                matchRepo.findByCategoryAndRound(category, nextRound);

        if (nextMatches.isEmpty()) return;

        int nextIndex = (int) Math.ceil(match.getMatchIndex() / 2.0);

        TournamentMatch target = nextMatches.stream()
                .filter(m -> m.getMatchIndex() == nextIndex)
                .findFirst()
                .orElse(null);

        if (target == null) return;

        if (match.getMatchIndex() % 2 == 1) {
            target.setParticipant1Id(match.getWinnerId());
            target.setParticipant1Name(match.getWinnerName());
        } else {
            target.setParticipant2Id(match.getWinnerId());
            target.setParticipant2Name(match.getWinnerName());
        }

        matchRepo.save(target);
    }

    /**
     * Sau khi 1 rubber CLUB FINISHED → kiểm tra:
     * - Đếm rubber wins của 2 CLB trong tie
     * - Nếu 1 CLB đạt majority (ceil(N/2)) → SKIP rubber còn lại + advance CLB winner
     * - Nếu hòa rubber count và đã play hết → tie-break theo tổng set won
     */
    private void handleClubTieAfterRubber(TournamentMatch finishedRubber) {
        String tieId = finishedRubber.getTieId();
        if (tieId == null) return;

        TournamentCategory category = finishedRubber.getCategory();
        List<TournamentMatch> rubbers = matchRepo.findByCategoryAndRound(category, finishedRubber.getRound())
                .stream()
                .filter(m -> tieId.equals(m.getTieId()))
                .toList();
        if (rubbers.isEmpty()) return;

        String club1Id = finishedRubber.getParticipant1Id();
        String club2Id = finishedRubber.getParticipant2Id();
        if (club1Id == null || club2Id == null) return; // bye, no advance needed (handled by gen)

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
                List<Integer> s1 = m.getSetScoreP1(), s2 = m.getSetScoreP2();
                int len = Math.min(s1 == null ? 0 : s1.size(), s2 == null ? 0 : s2.size());
                for (int i = 0; i < len; i++) {
                    if (s1.get(i) > s2.get(i)) club1Sets++;
                    else if (s2.get(i) > s1.get(i)) club2Sets++;
                }
            }
        }

        String tieWinnerId = null;
        if (club1Wins >= majorityThreshold) tieWinnerId = club1Id;
        else if (club2Wins >= majorityThreshold) tieWinnerId = club2Id;
        else if (finishedCount == totalRubbers) {
            // Hết rubber rồi, hòa → tie-break theo set
            if (club1Sets > club2Sets) tieWinnerId = club1Id;
            else if (club2Sets > club1Sets) tieWinnerId = club2Id;
        }

        if (tieWinnerId == null) return; // tie chưa kết thúc

        // SKIP các rubber chưa đánh
        for (TournamentMatch m : rubbers) {
            if (m.getStatus() == null || m.getStatus() == MatchStatus.NOT_STARTED) {
                m.setStatus(MatchStatus.SKIPPED);
                matchRepo.save(m);
            }
        }

        // Set CLB loser ELIMINATED
        String tieLoserId = tieWinnerId.equals(club1Id) ? club2Id : club1Id;
        clubParticipantRepo.findById(tieLoserId).ifPresent(loser -> {
            loser.setStatus(ClubTournamentParticipantStatusEnum.ELIMINATED);
            clubParticipantRepo.save(loser);
        });

        // Advance: tìm tie ở next round có cùng matchIndex slot
        advanceClubTie(category, finishedRubber.getRound(), finishedRubber.getMatchIndex(), tieWinnerId);
    }

    /**
     * Advance CLB winner từ tie (round, matchIndex) sang next round's tie.
     * Cập nhật participant1Id/2Id (= clubParticipantId) cho TẤT CẢ rubber của tie kế tiếp,
     * và tính lại participant1Name/2Name dựa trên lineup của CLB.
     */
    private void advanceClubTie(TournamentCategory category, int round, int matchIndex, String winnerClubId) {
        int nextRound = round + 1;
        int nextIndex = (int) Math.ceil(matchIndex / 2.0);
        boolean asP1 = matchIndex % 2 == 1;

        List<TournamentMatch> nextRubbers = matchRepo.findByCategoryAndRound(category, nextRound)
                .stream()
                .filter(m -> m.getMatchIndex() != null && m.getMatchIndex() == nextIndex)
                .toList();
        if (nextRubbers.isEmpty()) return; // final

        // Load winner club + lineup để build display name cho từng rubber
        ClubTournamentParticipant winnerClub = clubParticipantRepo.findById(winnerClubId).orElse(null);
        if (winnerClub == null) return;

        // Roster lookup
        List<ClubTournamentRoster> rosterList = winnerClub.getRoster();
        if (rosterList == null || rosterList.isEmpty()) {
            rosterList = clubRosterRepo.findByClubTournamentParticipant(winnerClub);
        }
        java.util.Map<String, ClubTournamentRoster> rosterByPos = new java.util.HashMap<>();
        for (ClubTournamentRoster r : rosterList) {
            if (r.getPosition() != null) rosterByPos.put(r.getPosition(), r);
        }

        for (TournamentMatch nextRubber : nextRubbers) {
            String displayName = buildRubberName(winnerClub, nextRubber, rosterByPos);
            if (asP1) {
                nextRubber.setParticipant1Id(winnerClubId);
                nextRubber.setParticipant1Name(displayName);
            } else {
                nextRubber.setParticipant2Id(winnerClubId);
                nextRubber.setParticipant2Name(displayName);
            }
            matchRepo.save(nextRubber);
        }
    }

    private String buildRubberName(
            ClubTournamentParticipant club,
            TournamentMatch rubber,
            java.util.Map<String, ClubTournamentRoster> rosterByPos) {
        com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum type = rubber.getLineType();
        Integer idx = rubber.getLineIndex();
        if (type == null || idx == null) return club.getClub().getName();
        if (type == com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum.SINGLES) {
            ClubTournamentRoster r = rosterByPos.get("SINGLES_" + idx);
            String name = r != null ? r.getClubMember().getAccount().getUserInfo().getFullName() : "?";
            return club.getClub().getName() + " - " + name;
        }
        ClubTournamentRoster r1 = rosterByPos.get(type.name() + "_" + idx + "_P1");
        ClubTournamentRoster r2 = rosterByPos.get(type.name() + "_" + idx + "_P2");
        String n1 = r1 != null ? r1.getClubMember().getAccount().getUserInfo().getFullName() : "?";
        String n2 = r2 != null ? r2.getClubMember().getAccount().getUserInfo().getFullName() : "?";
        return club.getClub().getName() + " - " + n1 + " / " + n2;
    }


    public BracketTreeResponse getBracketTree(String categoryId) {

        TournamentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new InvalidDataException("Category not found"));

        List<TournamentMatch> matches =
                matchRepo.findByCategory(category);

        if (matches.isEmpty()) {
            throw new InvalidDataException("Bracket has not been generated for this category");
        }

        int totalRounds = matches.stream()
                .mapToInt(TournamentMatch::getRound)
                .max()
                .orElse(1);

        List<BracketRoundResponse> rounds = new ArrayList<>();

        for (int roundNum = 1; roundNum <= totalRounds; roundNum++) {

            int finalRoundNum = roundNum;
            List<TournamentMatchResponse> matchResponses = matches.stream()
                    .filter(m -> m.getRound() == finalRoundNum)
                    .sorted(Comparator.comparing(TournamentMatch::getMatchIndex))
                    .map(this::convertToResponse)
                    .toList();

            rounds.add(BracketRoundResponse.builder()
                    .round(roundNum)
                    .matches(matchResponses)
                    .build());
        }

        return BracketTreeResponse.builder()
                .categoryId(categoryId)
                .categoryName(category.getCategory().name())
                .totalRounds(totalRounds)
                .rounds(rounds)
                .build();
    }

    private TournamentMatchResponse convertToResponse(TournamentMatch m) {

        return TournamentMatchResponse.builder()
                .matchId(m.getId())
                .round(m.getRound())
                .matchIndex(m.getMatchIndex())

                .player1Id(m.getParticipant1Id())
                .player2Id(m.getParticipant2Id())

                .player1Name(m.getParticipant1Name())
                .player2Name(m.getParticipant2Name())

                .setScoreP1(m.getSetScoreP1())
                .setScoreP2(m.getSetScoreP2())

                .winnerId(m.getWinnerId())
                .winnerName(m.getWinnerName())

                .status(m.getStatus().name())
                .build();
    }


}
