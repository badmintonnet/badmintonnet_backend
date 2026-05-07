package com.tlcn.sportsnet_backend.dto.tournament;

import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TournamentCreateRequest {
    String name;
    String description;
    String location;
    String facilityId;
    String bannerUrl;
    String logoUrl;
    BigDecimal fee;
    String rules;
    LocalDateTime startDate;
    LocalDateTime endDate;
    LocalDateTime registrationStartDate;
    LocalDateTime registrationEndDate;

    // Loại hình tournament: cá nhân hoặc CLB
    TournamentParticipationTypeEnum participationType;

    // Danh sách các hạng mục thi đấu (chỉ dùng khi participationType = INDIVIDUAL)
    List<TournamentCategoryRequest> categories;

    // ==================== CLUB TOURNAMENT FIELDS ====================
    // Chỉ dùng khi participationType = CLUB

    /**
     * Format trận đấu team (JSON)
     * Ví dụ: {"singles": 3, "menDoubles": 2, "womenDoubles": 1, "mixedDoubles": 1}
     */
    String teamMatchFormat;

    /**
     * Phí đăng ký cho CLB
     */
    BigDecimal clubRegistrationFee;

    /**
     * Số thành viên tối thiểu trong roster
     */
    Integer minClubRosterSize;

    /**
     * Số thành viên tối đa trong roster
     */
    Integer maxClubRosterSize;

    /**
     * Số CLB tối đa tham gia
     */
    Integer maxClubs;
}
