package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.ClubEvent;
import com.tlcn.sportsnet_backend.entity.ClubEventParticipant;
import com.tlcn.sportsnet_backend.enums.ClubEventParticipantStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ClubEventParticipantRepository extends JpaRepository<ClubEventParticipant, String> {
    boolean existsByClubEventAndParticipant(ClubEvent clubEvent, Account account);
    boolean existsByClubEventAndParticipantAndStatusNot(ClubEvent clubEvent, Account account, ClubEventParticipantStatusEnum status);

    Page<ClubEventParticipant> findAllByClubEvent(ClubEvent clubEvent, Pageable pageable);
    Optional<ClubEventParticipant> findByClubEventAndParticipant(ClubEvent clubEvent, Account account);
    Optional<ClubEventParticipant> findByClubEvent_IdAndParticipant(String id, Account account);
    List<ClubEventParticipant> findAllByClubEventOrderByJoinedAtDesc(ClubEvent clubEvent);

    @EntityGraph(attributePaths = {
            "club"
    })
    Page<ClubEventParticipant> findByParticipant_Id(String accountId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "clubEvent",
            "clubEvent.categories",
            "clubEvent.club",
            "clubEvent.club.tags"
    })
    List<ClubEventParticipant> findTop30ByParticipant_IdAndStatusNotOrderByJoinedAtDesc(
            String accountId,
            ClubEventParticipantStatusEnum status
    );

    @Query("SELECT COUNT(p) " +
            "FROM ClubEventParticipant p " +
            "WHERE p.clubEvent.club.id = :clubId " +
            "AND p.status = :status")
    Long  countByClubIdAndStatus(@Param("clubId") String clubId,
                                @Param("status") ClubEventParticipantStatusEnum status);

    @Query("""
    SELECT DISTINCT p.participant
    FROM ClubEventParticipant p
    WHERE p.clubEvent.club.id = :clubId
      AND p.participant.id NOT IN (
          SELECT m.account.id
          FROM ClubMember m
          WHERE m.club.id = :clubId
      )
""")
    List<Account> findDistinctNonMemberParticipantsByClubId(@Param("clubId") String clubId);

    @Query("""
    SELECT COUNT(p)
    FROM ClubEventParticipant p
    WHERE p.participant.id = :accountId
      AND p.clubEvent.club.id = :clubId
      AND p.status = :status
""")
    long countEventsByParticipantInClub(@Param("accountId") String accountId,
                                        @Param("clubId") String clubId,
                                        @Param("status") ClubEventParticipantStatusEnum status);

}
