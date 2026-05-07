package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.ClubMember;
import com.tlcn.sportsnet_backend.entity.ClubTournamentParticipant;
import com.tlcn.sportsnet_backend.entity.ClubTournamentRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubTournamentRosterRepository extends JpaRepository<ClubTournamentRoster, String> {

    // Lấy tất cả thành viên trong roster của một participant
    List<ClubTournamentRoster> findByClubTournamentParticipant(ClubTournamentParticipant participant);

    // Tìm roster entry theo participant + position (không cần fetch vì đã có)
    @Query("SELECT r FROM ClubTournamentRoster r " +
           "WHERE r.clubTournamentParticipant.id = :participantId " +
           "AND r.position = :position")
    Optional<ClubTournamentRoster> findByClubTournamentParticipant_IdAndPosition(
            @Param("participantId") String participantId,
            @Param("position") String position
    );

    // Tìm roster entry với full eager fetch (clubMember + account + userInfo)
    @Query("SELECT r FROM ClubTournamentRoster r " +
           "LEFT JOIN FETCH r.clubMember cm " +
           "LEFT JOIN FETCH cm.account acc " +
           "LEFT JOIN FETCH acc.userInfo " +
           "WHERE r.clubTournamentParticipant.id = :participantId " +
           "AND r.position = :position")
    Optional<ClubTournamentRoster> findByClubTournamentParticipant_IdAndPositionWithDetails(
            @Param("participantId") String participantId,
            @Param("position") String position
    );
}
