package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.TournamentParticipantEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentTeamRepository extends JpaRepository<TournamentTeam, String> {
    @Query("""
    SELECT i FROM TournamentTeam i
    WHERE i.category.id = :categoryId
      AND (i.player1 = :account OR i.player2 = :account)
""")
    Optional<TournamentTeam> findByCategoryAndAccount(
            @Param("categoryId") String categoryId,
            @Param("account") Account account
    );
    @Query("""
    SELECT COUNT(i)>0 FROM TournamentTeam i
    WHERE i.category.id = :categoryId
      AND (i.player1 = :account OR i.player2 = :account) AND i.status != :status
""")
    boolean existsByAccountAndCategory(@Param("categoryId") String categoryId,
                                       @Param("account") Account account,
                                       @Param("status") TournamentParticipantEnum status);
    @Query("""
    SELECT COUNT(i)>0 FROM TournamentTeam i
    WHERE i.category.id = :categoryId
      AND (i.player1 = :account OR i.player2 = :account)
""")
    boolean existsByAccountAndCategory(@Param("categoryId") String categoryId,
                                       @Param("account") Account account
                                       );
    int countByCategory(TournamentCategory category);

    Page<TournamentTeam> findByCategoryId(String categoryId, Pageable pageable);

    Page<TournamentTeam> findByCategoryIdAndStatusIn(
            String categoryId,
            List<TournamentParticipantEnum> status,
            Pageable pageable
    );

    List<TournamentTeam> findByCategory(TournamentCategory category);
}
