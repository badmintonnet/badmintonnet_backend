package com.tlcn.sportsnet_backend.dto.club_tournament;

import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubTournamentParticipantResponse {

    String id;

    // Club info
    String clubId;
    String clubName;
    String clubLogoUrl;
    String clubSlug;
    String clubLocation;
    String ownerName;
    String ownerEmail;

    // Tournament info
    String tournamentId;
    String tournamentName;
    String tournamentSlug;
    /** Trạng thái giải (UPCOMING…COMPLETED); FE dùng để hiển thị kết quả khi CLB vô địch vẫn APPROVED */
    TournamentStatus tournamentStatus;

    // Participation info
    ClubTournamentParticipantStatusEnum status;
    Instant registeredAt;
    boolean paid;

    // Roster
    List<ClubRosterMemberResponse> roster;
    int rosterSize;
}
