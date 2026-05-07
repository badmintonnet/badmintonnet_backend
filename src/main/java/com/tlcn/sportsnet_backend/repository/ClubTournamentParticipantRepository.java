package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubTournamentParticipant;
import com.tlcn.sportsnet_backend.entity.Tournament;
import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubTournamentParticipantRepository extends JpaRepository<ClubTournamentParticipant, String> {

    // Fetch voi club + owner + userInfo, tournament de tranh Lazy loading
    @Query("SELECT ctp FROM ClubTournamentParticipant ctp " +
           "LEFT JOIN FETCH ctp.club club " +
           "LEFT JOIN FETCH club.owner owner " +
           "LEFT JOIN FETCH owner.userInfo " +
           "LEFT JOIN FETCH ctp.tournament t " +
           "WHERE ctp.id = :id")
    Optional<ClubTournamentParticipant> findByIdWithDetails(@Param("id") String id);

    // Kiểm tra CLB đã đăng ký tournament này chưa
    boolean existsByClubAndTournament(Club club, Tournament tournament);

    // Đếm số CLB đã đăng ký tournament
    int countByTournament(Tournament tournament);

    // Tìm đăng ký của CLB trong tournament - với eager fetch
    @Query("SELECT ctp FROM ClubTournamentParticipant ctp " +
           "LEFT JOIN FETCH ctp.club club " +
           "LEFT JOIN FETCH club.owner owner " +
           "LEFT JOIN FETCH owner.userInfo " +
           "LEFT JOIN FETCH ctp.tournament t " +
           "WHERE club = :club AND t = :tournament")
    Optional<ClubTournamentParticipant> findByClubAndTournament(@Param("club") Club club, @Param("tournament") Tournament tournament);

    // Lấy danh sách CLB đã đăng ký theo tournament (phân trang) - với eager fetch + roster
    @Query("SELECT DISTINCT ctp FROM ClubTournamentParticipant ctp " +
           "LEFT JOIN FETCH ctp.club club " +
           "LEFT JOIN FETCH club.owner owner " +
           "LEFT JOIN FETCH owner.userInfo " +
           "LEFT JOIN FETCH ctp.tournament t " +
           "LEFT JOIN FETCH ctp.roster roster " +
           "LEFT JOIN FETCH roster.clubMember cm " +
           "LEFT JOIN FETCH cm.account " +
           "WHERE ctp.tournament.id = :tournamentId")
    Page<ClubTournamentParticipant> findByTournamentId(@Param("tournamentId") String tournamentId, Pageable pageable);

    // Lấy danh sách CLB theo tournament và status (phân trang) - với eager fetch + roster
    @Query("SELECT DISTINCT ctp FROM ClubTournamentParticipant ctp " +
           "LEFT JOIN FETCH ctp.club club " +
           "LEFT JOIN FETCH club.owner owner " +
           "LEFT JOIN FETCH owner.userInfo " +
           "LEFT JOIN FETCH ctp.tournament t " +
           "LEFT JOIN FETCH ctp.roster roster " +
           "LEFT JOIN FETCH roster.clubMember cm " +
           "LEFT JOIN FETCH cm.account " +
           "WHERE ctp.tournament.id = :tournamentId AND ctp.status IN :statuses")
    Page<ClubTournamentParticipant> findByTournamentIdAndStatusIn(
            @Param("tournamentId") String tournamentId,
            @Param("statuses") List<ClubTournamentParticipantStatusEnum> statuses,
            Pageable pageable
    );

    // Lấy tất cả CLB trong tournament (không phân trang)
    List<ClubTournamentParticipant> findByTournament(Tournament tournament);

    // Lấy danh sách CLB theo status
    @Query("SELECT ctp FROM ClubTournamentParticipant ctp WHERE ctp.tournament.id = :tournamentId AND ctp.status = :status")
    List<ClubTournamentParticipant> findByTournamentIdAndStatus(
            @Param("tournamentId") String tournamentId,
            @Param("status") ClubTournamentParticipantStatusEnum status
    );

    // Lấy tất cả CLB của một tournament
    @Query("SELECT ctp FROM ClubTournamentParticipant ctp WHERE ctp.tournament.id = :tournamentId")
    List<ClubTournamentParticipant> findByTournamentId(@Param("tournamentId") String tournamentId);

    // Lấy danh sách CLB của một club owner
    @Query("SELECT ctp FROM ClubTournamentParticipant ctp WHERE ctp.club.owner.id = :ownerId")
    Page<ClubTournamentParticipant> findByClubOwnerId(@Param("ownerId") String ownerId, Pageable pageable);

    // Lấy tất cả tournament của một club với eager fetch (dùng cho My Club - Tab Giải đấu)
    @Query("SELECT DISTINCT ctp FROM ClubTournamentParticipant ctp " +
           "LEFT JOIN FETCH ctp.club club " +
           "LEFT JOIN FETCH club.owner owner " +
           "LEFT JOIN FETCH owner.userInfo " +
           "LEFT JOIN FETCH ctp.tournament t " +
           "LEFT JOIN FETCH ctp.roster roster " +
           "LEFT JOIN FETCH roster.clubMember cm " +
           "LEFT JOIN FETCH cm.account " +
           "WHERE ctp.club.id = :clubId " +
           "ORDER BY ctp.registeredAt DESC")
    List<ClubTournamentParticipant> findByClubIdWithDetails(@Param("clubId") String clubId);

    // Đếm số CLB đã được approve trong tournament
    @Query("SELECT COUNT(ctp) FROM ClubTournamentParticipant ctp " +
           "WHERE ctp.tournament.id = :tournamentId AND ctp.status = :status")
    long countByTournamentIdAndStatus(
            @Param("tournamentId") String tournamentId,
            @Param("status") ClubTournamentParticipantStatusEnum status);
}
