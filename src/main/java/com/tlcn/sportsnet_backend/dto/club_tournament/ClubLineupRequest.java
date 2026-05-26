package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

/**
 * Request set/update lineup cho club participant.
 * Key = position (e.g. "SINGLES_1", "MEN_DOUBLES_1_P1"), value = rosterEntryId.
 * Cho phép submit partial — service sẽ ghi đè các vị trí được gửi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubLineupRequest {
    /** Map position → rosterEntryId. Submit null/empty value để clear position. */
    Map<String, String> lineup;
}
