package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubBracketRoundResponse {
    int round;
    /** Legacy: matches truyền thống (1 match/cặp). Giữ cho backward compat. */
    List<ClubBracketMatchResponse> matches;
    /** New: ties (mỗi tie có thể chứa nhiều rubber). */
    List<ClubBracketTieResponse> ties;
}
