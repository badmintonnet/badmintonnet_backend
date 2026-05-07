package com.tlcn.sportsnet_backend.entity;

import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "tournament_payments")
public class TournamentPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    // Người tham gia nào trả tiền
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    TournamentParticipant participant;

    // Người tham gia nào trả tiền
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    TournamentTeam team;

    // CLB tham gia thanh toán (cho CLUB tournament)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_participant_id")
    ClubTournamentParticipant clubTournamentParticipant;

    // Mã giao dịch bạn gửi cho VNPAY (vnp_TxnRef)
    String txnRef;

    // Mã giao dịch VNPay trả về (vnp_TransactionNo)
    String vnpTransactionNo;

    Double amount;

    @Enumerated(EnumType.STRING)
    PaymentStatusEnum status;  // PENDING, SUCCESS, FAILED

    String bankCode;
    String cardType;

    Instant createdAt;
    Instant paidAt;

    @PrePersist
    public void beforeCreate() {
        createdAt = Instant.now();
        status = PaymentStatusEnum.PENDING;
    }
}
