package com.tlcn.sportsnet_backend.dto.payment;

import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VNPayReturnResponse {
    String status;
    String tournamentId;
    String categoryId;
    String participantId;  // ClubTournamentParticipant.id for club payments
    TournamentParticipationTypeEnum participationType;  // INDIVIDUAL or CLUB
}
