package com.tlcn.sportsnet_backend.entity;

import com.tlcn.sportsnet_backend.util.SecurityUtil;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "club_tournament_rosters",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_tournament_participant_id", "club_member_id"}))
public class ClubTournamentRoster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_tournament_participant_id", nullable = false)
    ClubTournamentParticipant clubTournamentParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_member_id", nullable = false)
    ClubMember clubMember;

    // Position/role trong roster (e.g., "SINGLES", "DOUBLES", "SUBSTITUTE")
    String position;

    Instant addedAt;

    // Cho phép thay đổi roster trước deadline
    @Builder.Default
    Boolean canModify = true;

    String createdBy;
    Instant updatedAt;
    String updatedBy;

    @PrePersist
    public void handleBeforeCreate() {
        addedAt = Instant.now();
        createdBy = SecurityUtil.getCurrentUserLogin().orElse("");
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        updatedAt = Instant.now();
        updatedBy = SecurityUtil.getCurrentUserLogin().orElse("");
    }
}
