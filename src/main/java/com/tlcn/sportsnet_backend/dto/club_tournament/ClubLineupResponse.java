package com.tlcn.sportsnet_backend.dto.club_tournament;

import com.tlcn.sportsnet_backend.dto.tournament.TeamMatchFormatDTO;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClubLineupResponse {
    String participantId;
    String clubId;
    String clubName;

    /** Format yêu cầu của tournament (singles, menDoubles, ...) */
    TeamMatchFormatDTO format;

    /** Tất cả slot (đã + chưa chọn). */
    List<ClubLineupSlotResponse> slots;

    /** Đếm số slot đã chọn / tổng. */
    int filledCount;
    int totalSlots;

    /** Đã đầy đủ chưa (tất cả slot có rosterEntryId). */
    boolean complete;

    /** Lineup đã bị khoá (sau khi admin generate bracket cho tournament này). */
    boolean locked;
}
