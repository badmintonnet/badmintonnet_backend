package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubRosterMemberResponse {

    String rosterEntryId;      // ID của ClubTournamentRoster entry
    String clubMemberId;       // ID của ClubMember
    String accountId;
    String fullName;
    String email;
    String avatarUrl;
    String slug;
    String skillLevel;
    String role;               // OWNER | MEMBER
    String position;           // SINGLES | DOUBLES | SUBSTITUTE (tuỳ chọn)
    Boolean canModify;
}
