package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Tournament;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.NonNullApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, String> {
    @NonNull
    @EntityGraph(attributePaths = {
            "categories",
            "facility"
    })
    Page<Tournament> findAllByStatusNot(Pageable pageable, TournamentStatus status);

    // Filter by status + participationType
    @NonNull
    @EntityGraph(attributePaths = {
            "categories",
            "facility"
    })
    @Query("SELECT t FROM Tournament t WHERE t.status != :status " +
           "AND (:participationType IS NULL OR t.participationType = :participationType)")
    Page<Tournament> findAllByStatusNotAndParticipationType(
            Pageable pageable,
            @Param("status") TournamentStatus status,
            @Param("participationType") TournamentParticipationTypeEnum participationType
    );

    @NonNull
    Page<Tournament> findAll(Pageable pageable);

    @NonNull
    @EntityGraph(attributePaths = {
            "categories",
            "facility"
    })
    @Query("""
            SELECT t
            FROM Tournament t
            WHERE t.status <> :excludedStatus
            AND (:participationType IS NULL
                 OR COALESCE(t.participationType, com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum.INDIVIDUAL) = :participationType)
            AND (:content IS NULL OR TRIM(:content) = ''
                 OR LOWER(t.name) LIKE LOWER(CONCAT('%', :content, '%'))
                 OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :content, '%')))
            AND (:organizationDateFrom IS NULL OR t.startDate >= :organizationDateFrom)
            AND (:organizationDateTo IS NULL OR t.startDate <= :organizationDateTo)
            """)
    Page<Tournament> searchTournaments(
            Pageable pageable,
            @Param("excludedStatus") TournamentStatus excludedStatus,
            @Param("participationType") TournamentParticipationTypeEnum participationType,
            @Param("content") String content,
            @Param("organizationDateFrom") LocalDateTime organizationDateFrom,
            @Param("organizationDateTo") LocalDateTime organizationDateTo
    );

    Optional<Tournament> findBySlug(String slug);
    @Query("SELECT t FROM Tournament t JOIN t.categories c WHERE c.id = :categoryId")
    Optional<Tournament> findByCategoryId(@Param("categoryId") String categoryId);
    @EntityGraph(attributePaths = {})
    @Query("SELECT e FROM Tournament e WHERE e.status NOT IN :statuses")
    List<Tournament> findAllByStatusNot(@Param("statuses") List<TournamentStatus> excludedStatuses);

    @EntityGraph(attributePaths = {
            "facility",
            "categories"
    })
    @Query("""
            SELECT t FROM Tournament t
            JOIN t.facility f
            WHERE t.status <> :excludedStatus
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
    List<Tournament> findNearestTournaments(
            @Param("excludedStatus") TournamentStatus excludedStatus,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "facility",
            "categories"
    })
    @Query("""
            SELECT DISTINCT t FROM Tournament t
            WHERE t.status NOT IN :excludedStatuses
              AND t.endDate > :now
            ORDER BY t.startDate ASC
            """)
    List<Tournament> findRecommendationCandidates(
            @Param("excludedStatuses") List<TournamentStatus> excludedStatuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    // Fetch tournament by ID for Club Tournament
    @Query("SELECT t FROM Tournament t WHERE t.id = :id")
    Optional<Tournament> findByIdForClubTournament(@Param("id") String id);
}
