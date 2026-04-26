package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubMatchParticipantResponse {
    String participantId;   // ClubTournamentParticipant.id (stored in match.participant1Id)
    String clubId;
    String clubName;
    String clubLogoUrl;
    String memberId;         // Account.id
    String memberName;
    String memberAvatarUrl;
}
