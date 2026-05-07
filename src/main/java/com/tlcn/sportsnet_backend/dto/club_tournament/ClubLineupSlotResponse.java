package com.tlcn.sportsnet_backend.dto.club_tournament;

import com.tlcn.sportsnet_backend.enums.ClubLineTypeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubLineupSlotResponse {
    /** Vị trí, e.g. "SINGLES_1", "MEN_DOUBLES_1_P1". */
    String position;
    ClubLineTypeEnum lineType;
    Integer lineIndex;
    /** Player slot trong rubber, 1 hoặc 2 (null cho singles). */
    Integer playerSlot;

    /** rosterEntryId được gán (null nếu chưa chọn). */
    String rosterEntryId;
    String accountId;
    String fullName;
    String avatarUrl;
}
