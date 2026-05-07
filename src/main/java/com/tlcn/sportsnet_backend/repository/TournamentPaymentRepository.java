package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.ClubTournamentParticipant;
import com.tlcn.sportsnet_backend.entity.TournamentParticipant;
import com.tlcn.sportsnet_backend.entity.TournamentPayment;
import com.tlcn.sportsnet_backend.entity.TournamentTeam;
import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentPaymentRepository extends JpaRepository<TournamentPayment, String> {
    Optional<TournamentPayment> findByTxnRef(String txnRef);

    boolean existsByParticipantAndStatus(TournamentParticipant participant, PaymentStatusEnum status);
    boolean existsByTeamAndStatus(TournamentTeam tournamentTeam, PaymentStatusEnum status);

    // Club tournament payment queries
    Optional<TournamentPayment> findByClubTournamentParticipant_Id(String participantId);
    boolean existsByClubTournamentParticipantAndStatus(ClubTournamentParticipant ctp, PaymentStatusEnum status);
}
