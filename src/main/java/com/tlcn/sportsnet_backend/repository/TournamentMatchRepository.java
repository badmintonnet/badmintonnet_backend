package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.TournamentCategory;
import com.tlcn.sportsnet_backend.entity.TournamentMatch;
import com.tlcn.sportsnet_backend.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, String> {
    List<TournamentMatch> findByCategoryAndRound(TournamentCategory category, Integer round);

    List<TournamentMatch> findByCategory(TournamentCategory category);

    @Query("""
            SELECT m FROM TournamentMatch m
            WHERE m.category.id = :categoryId
              AND m.status = :status
              AND (m.participant1Id = :entrantId OR m.participant2Id = :entrantId)
            """)
    List<TournamentMatch> findByCategoryIdAndEntrantIdAndStatus(
            @Param("categoryId") String categoryId,
            @Param("entrantId") String entrantId,
            @Param("status") MatchStatus status
    );

    void deleteByCategory(TournamentCategory category);

    @Query("""
    SELECT MAX(m.round)
    FROM TournamentMatch m
    WHERE m.category = :category
""")
    Integer findMaxRoundByCategory(@Param("category") TournamentCategory category);

}
