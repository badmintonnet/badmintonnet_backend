package com.tlcn.sportsnet_backend.dto.club_tournament;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateRosterRequest {

    // Danh sách account IDs mới cho roster (thay thế toàn bộ roster hiện tại)
    List<String> rosterAccountIds;

    // Legacy - for backward compatibility
    @Deprecated
    List<String> rosterMemberIds;
}
