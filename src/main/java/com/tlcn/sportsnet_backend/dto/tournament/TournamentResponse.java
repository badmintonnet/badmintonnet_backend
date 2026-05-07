package com.tlcn.sportsnet_backend.dto.tournament;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TournamentResponse {

    String id;
    String name;
    String description;

    String location;
    FacilityResponse facility;

    String slug;

    LocalDateTime startDate;

    LocalDateTime endDate;

    LocalDateTime registrationStartDate;

    LocalDateTime registrationEndDate;

    String logoUrl;
    String bannerUrl;

    Instant createdAt;
    TournamentStatus status;
    TournamentParticipationTypeEnum participationType;
    BigDecimal fee;
    String createdBy;
    List<TournamentCategoryResponse> categories;

    // ==================== CLUB TOURNAMENT FIELDS ====================
    // Only populated when participationType = CLUB
    String teamMatchFormat;
    BigDecimal clubRegistrationFee;
    Integer minClubRosterSize;
    Integer maxClubRosterSize;
    Integer maxClubs;
}
