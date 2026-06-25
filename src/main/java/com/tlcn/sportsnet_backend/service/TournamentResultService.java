package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.tournament_result.CategoryResultItemResponse;
import com.tlcn.sportsnet_backend.dto.tournament_result.CategoryResultResponse;
import com.tlcn.sportsnet_backend.dto.tournament_result.TournamentResultResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentResultService {
    private final TournamentRepository tournamentRepo;
    private final TournamentCategoryRepository categoryRepo;
    private final TournamentResultRepository resultRepo;
    private final TournamentMatchRepository matchRepo;
    private final TournamentParticipantRepository participantRepo;
    private final TournamentTeamRepository teamRepo;
    private final AdminNotificationService adminNotificationService;

    public TournamentResultResponse getTournamentResults(String tournamentId) {

        Tournament tournament = tournamentRepo.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        List<TournamentCategory> categories =
                categoryRepo.findByTournament(tournament);

        List<CategoryResultResponse> categoryResults = new ArrayList<>();

        for (TournamentCategory category : categories) {

            List<TournamentResult> results =
                    resultRepo.findByCategory(category);

            List<CategoryResultItemResponse> items = results.stream()
                    .sorted(Comparator.comparing(TournamentResult::getRanking))
                    .map(r -> CategoryResultItemResponse.builder()
                            .ranking(r.getRanking())
                            .prize(r.getPrize())

                            // SINGLE
                            .participantId(
                                    r.getParticipant() != null
                                            ? r.getParticipant().getId()
                                            : null
                            )
                            .participantName(
                                    r.getParticipant() != null
                                            ? r.getParticipant().getDisplayName()
                                            : null
                            )

                            // DOUBLE
                            .teamId(
                                    r.getTeam() != null
                                            ? r.getTeam().getId()
                                            : null
                            )
                            .teamName(
                                    r.getTeam() != null
                                            ? r.getTeam().getDisplayName()
                                            : null
                            )
                            .build()
                    )
                    .toList();

            categoryResults.add(
                    CategoryResultResponse.builder()
                            .categoryId(category.getId())
                            .categoryName(category.getCategory().getLabel())
                            .results(items)
                            .build()
            );
        }

        return TournamentResultResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .categories(categoryResults)
                .build();
    }

    @Transactional
    public void generateResultsByTournament(String tournamentId) {

        Tournament tournament = tournamentRepo.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        for (TournamentCategory category : tournament.getCategories()) {

            // Xoá kết quả cũ (nếu regenerate)
            resultRepo.deleteByCategory(category);

            generateResultForCategory(category);
        }

        adminNotificationService.notifyAllAdmins(
                "Kết quả giải đấu đã được tổng hợp",
                "Kết quả giải đấu \"" + tournament.getName() + "\" đã được tổng hợp xong và sẵn sàng để xem xét.",
                "/admin/tournaments"
        );
    }

    public CategoryResultResponse getCategoryResult(String categoryId) {

        TournamentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        List<TournamentResult> results =
                resultRepo.findByCategory(category);

        if (results.isEmpty()) {
            return CategoryResultResponse.builder()
                    .categoryId(category.getId())
                    .categoryName(category.getCategory().getLabel())
                    .results(List.of())
                    .build();
        }

        List<CategoryResultItemResponse> items = results.stream()
                .sorted(Comparator.comparing(TournamentResult::getRanking))
                .map(r -> CategoryResultItemResponse.builder()
                        .ranking(r.getRanking())
                        .prize(r.getPrize())

                        // SINGLE
                        .participantId(
                                r.getParticipant() != null
                                        ? r.getParticipant().getId()
                                        : null
                        )
                        .participantName(
                                r.getParticipant() != null
                                        ? r.getParticipant().getDisplayName()
                                        : null
                        )

                        // DOUBLE
                        .teamId(
                                r.getTeam() != null
                                        ? r.getTeam().getId()
                                        : null
                        )
                        .teamName(
                                r.getTeam() != null
                                        ? r.getTeam().getDisplayName()
                                        : null
                        )
                        .build()
                )
                .toList();

        return CategoryResultResponse.builder()
                .categoryId(category.getId())
                .categoryName(category.getCategory().getLabel())
                .results(items)
                .build();
    }


    public void generateResultForCategory(TournamentCategory category) {

        // Round Final
        Integer finalRound = matchRepo.findMaxRoundByCategory(category);
        if (finalRound == null) return;

        // 2️⃣ Trận Final
        TournamentMatch finalMatch = matchRepo
                .findByCategoryAndRound(category, finalRound)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Final match not found"));

        if (finalMatch.getWinnerId() == null) {
            throw new RuntimeException("Final match not finished");
        }

        // Hạng 1
        saveResult(category, 1, finalMatch.getWinnerId());

        // Hạng 2
        String runnerUpId = finalMatch.getWinnerId().equals(finalMatch.getParticipant1Id())
                ? finalMatch.getParticipant2Id()
                : finalMatch.getParticipant1Id();

        if (runnerUpId != null) {
            saveResult(category, 2, runnerUpId);
        }

        // Hạng 3 (thua bán kết)
        List<TournamentMatch> semiFinals =
                matchRepo.findByCategoryAndRound(category, finalRound - 1);

        for (TournamentMatch semi : semiFinals) {
            if (semi.getWinnerId() == null) continue;

            String loserId = semi.getWinnerId().equals(semi.getParticipant1Id())
                    ? semi.getParticipant2Id()
                    : semi.getParticipant1Id();

            if (loserId != null) {
                saveResult(category, 3, loserId);
            }
        }
    }


    private void saveResult(
            TournamentCategory category,
            int ranking,
            String winnerId
    ) {
        TournamentResult result = TournamentResult.builder()
                .ranking(ranking)
                .prize(getPrizeByRanking(category, ranking))
                .category(category)
                .build();

        if (category.getCategory().getType().equals("SINGLE")) {

            TournamentParticipant participant =
                    participantRepo.findById(winnerId)
                            .orElseThrow(() ->
                                    new RuntimeException("Participant not found: " + winnerId)
                            );

            result.setParticipant(participant);

        } else {

            TournamentTeam team =
                    teamRepo.findById(winnerId)
                            .orElseThrow(() ->
                                    new RuntimeException("Team not found: " + winnerId)
                            );

            result.setTeam(team);
        }

        resultRepo.save(result);
    }


    private String getPrizeByRanking(TournamentCategory category, int ranking) {
        return switch (ranking) {
            case 1 -> category.getFirstPrize();
            case 2 -> category.getSecondPrize();
            case 3 -> category.getThirdPrize();
            default -> null;
        };
    }
}
