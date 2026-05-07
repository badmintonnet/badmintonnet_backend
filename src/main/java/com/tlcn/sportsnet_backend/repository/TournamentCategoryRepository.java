package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Tournament;
import com.tlcn.sportsnet_backend.entity.TournamentCategory;
import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentCategoryRepository extends JpaRepository<TournamentCategory, String> {
    List<TournamentCategory> findByTournament(Tournament tournament);

    @Query("SELECT tc FROM TournamentCategory tc LEFT JOIN FETCH tc.tournament WHERE tc.id = :id")
    Optional<TournamentCategory> findByIdWithTournament(@Param("id") String id);

    @Query("SELECT tc FROM TournamentCategory tc " +
           "WHERE tc.tournament.id = :tournamentId " +
           "AND tc.category = :category")
    Optional<TournamentCategory> findByTournamentIdAndCategory(
            @Param("tournamentId") String tournamentId,
            @Param("category") BadmintonCategoryEnum category
    );
}
