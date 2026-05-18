package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.TournamentCategory;
import com.tlcn.sportsnet_backend.entity.TournamentParticipant;
import com.tlcn.sportsnet_backend.enums.TournamentParticipantEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, String> {
    boolean existsByAccountAndCategory(Account account, TournamentCategory category);
    int countByCategory(TournamentCategory category);

    TournamentParticipant findByAccountAndCategory(Account account, TournamentCategory category);

    Optional<TournamentParticipant> findByAccount_IdAndCategory_Id(String accountId, String categoryId);

    @EntityGraph(attributePaths = {
            "category",
            "category.tournament"
    })
    List<TournamentParticipant> findTop20ByAccount_IdOrderByCreatedAtDesc(String accountId);

    Page<TournamentParticipant> findByCategoryId(String categoryId, Pageable pageable);

    Page<TournamentParticipant> findByCategoryIdAndStatusIn(
            String categoryId,
            List<TournamentParticipantEnum> status,
            Pageable pageable
    );

    List<TournamentParticipant> findByCategory(TournamentCategory category);
}
