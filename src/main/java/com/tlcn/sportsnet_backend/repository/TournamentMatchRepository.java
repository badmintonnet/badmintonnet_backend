package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.TournamentCategory;
import com.tlcn.sportsnet_backend.entity.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, String> {
    List<TournamentMatch> findByCategoryAndRound(TournamentCategory category, Integer round);

    List<TournamentMatch> findByCategory(TournamentCategory category);

    void deleteByCategory(TournamentCategory category);

    @Query("""
    SELECT MAX(m.round)
    FROM TournamentMatch m
    WHERE m.category = :category
""")
    Integer findMaxRoundByCategory(@Param("category") TournamentCategory category);

}
