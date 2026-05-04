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

            // Nếu là CLUB tournament → set loser status = ELIMINATED
            if (match.getCategory().getTournament().getParticipationType()
                    == TournamentParticipationTypeEnum.CLUB) {
                String loserId = p1Winner
                        ? match.getParticipant2Id()
                        : match.getParticipant1Id();
                if (loserId != null) {
                    clubParticipantRepo.findById(loserId).ifPresent(loser -> {
                        loser.setStatus(ClubTournamentParticipantStatusEnum.ELIMINATED);
                        clubParticipantRepo.save(loser);
                    });
                }
            }

            advanceWinner(match);

            try {
                historyService.finishMatch(match.getId());
            } catch (Exception e) {
                log.error("Error saving match history for matchId={}", match.getId(), e);
            }

            // TỰ ĐỘNG GENERATE KẾT QUẢ NẾU LÀ FINAL
            Integer maxRound = matchRepo.findMaxRoundByCategory(match.getCategory());
            if (match.getRound().equals(maxRound)) {
                // CLUB tournament dùng TournamentMatch với participantId = ClubTournamentParticipant.id,
                // không tương thích với TournamentResultService (query TournamentParticipant).
                // CLUB results được derive on-the-fly bởi ClubTournamentResultService.
                TournamentParticipationTypeEnum type =
                        match.getCategory().getTournament().getParticipationType();
                if (type != TournamentParticipationTypeEnum.CLUB) {
                    resultService.generateResultForCategory(match.getCategory());

                    historyService.updateHistoryFromCategoryResult(
                            match.getCategory()
                    );
                }
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
