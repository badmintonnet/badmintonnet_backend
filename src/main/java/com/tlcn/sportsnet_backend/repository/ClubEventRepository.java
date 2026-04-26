package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.ClubEvent;

import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.enums.EventStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubEventRepository extends JpaRepository<ClubEvent, String>, JpaSpecificationExecutor<ClubEvent> {
    Page<ClubEvent> findByClub_Slug(String slug, Pageable pageable);
    @Override
    @EntityGraph(attributePaths = {
            "participants",
            "club",
            "facility",
            "categories"
    })
    Page<ClubEvent> findAll(Specification<ClubEvent> spec, Pageable pageable);
    Optional<ClubEvent> findBySlug(String slug);

    Page<ClubEvent> findAllByOpenForOutsideAndStatusAndDeadlineAfter(Pageable pageable, Boolean openForOutside, EventStatusEnum status, LocalDateTime deadline);
    List<ClubEvent> findAllByOpenForOutsideAndStatusAndDeadlineAfterOrderByCreatedAtDesc(Boolean openForOutside, EventStatusEnum status, LocalDateTime deadline);
    @EntityGraph(attributePaths = {
            "participants",
            "club",
            "facility",
            "categories"
    })
    Page<ClubEvent> findByClub_Members_Account_IdAndClub_Members_StatusAndStatusAndDeadlineAfter(
            String accountId,
            ClubMemberStatusEnum memberStatus,
            EventStatusEnum eventStatus,
            LocalDateTime now,
            Pageable pageable
    );
    @EntityGraph(attributePaths = {
            "participants",
            "club",
            "facility",
            "categories",
            "club.owner"
    })
    @Query("""
        SELECT e FROM ClubEvent e
        JOIN e.participants p
        WHERE p.participant.id = :participantId
    """)
    Page<ClubEvent> findByParticipantId(@Param("participantId") String participantId, Pageable pageable);


    @EntityGraph(attributePaths = {})
    @Query("SELECT e FROM ClubEvent e WHERE e.status NOT IN :statuses")
    List<ClubEvent> findAllByStatusNotIn(@Param("statuses") List<EventStatusEnum> statuses);


    @Query("SELECT COUNT(e) FROM ClubEvent e WHERE e.club.id = :clubId AND e.status = :status")
    Long  countByClubIdAndStatus(@Param("clubId") String clubId,
                                @Param("status") EventStatusEnum status);

    @Query("SELECT MAX(c.eventCount) FROM (SELECT COUNT(e) as eventCount FROM ClubEvent e WHERE e.status = :status GROUP BY e.club.id) c")
    Long  findMaxEventCount(@Param("status") EventStatusEnum status);

    @Query("SELECT SUM(e.totalMember) " +
            "FROM ClubEvent e " +
            "WHERE e.club.id = :clubId")
    Long  sumTotalMemberByClubId(@Param("clubId") String clubId);

    @EntityGraph(attributePaths = {
            "facility",
            "club"
    })
    @Query("""
            SELECT e FROM ClubEvent e
            JOIN e.facility f
            WHERE e.openForOutside = :openForOutside
              AND e.status = :status
              AND e.deadline > :now
              AND f.latitude IS NOT NULL
              AND f.longitude IS NOT NULL
            ORDER BY (
                6371 * acos(
                    cos(radians(:latitude)) * cos(radians(f.latitude))
                    * cos(radians(f.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(f.latitude))
                )
            ) ASC
            """)
    List<ClubEvent> findNearestEvents(
            @Param("openForOutside") boolean openForOutside,
            @Param("status") EventStatusEnum status,
            @Param("now") LocalDateTime now,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            Pageable pageable
    );



}
