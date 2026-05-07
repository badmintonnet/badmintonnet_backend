package com.tlcn.sportsnet_backend.entity;

import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.util.SecurityUtil;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "club_tournament_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "tournament_id"}))
public class ClubTournamentParticipant implements BracketParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    Tournament tournament;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    ClubTournamentParticipantStatusEnum status = ClubTournamentParticipantStatusEnum.DRAFT;

    Instant registeredAt;
    Instant updatedAt;
    String createdBy;
    String updatedBy;

    @OneToMany(mappedBy = "clubTournamentParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ClubTournamentRoster> roster = new ArrayList<>();

    @PrePersist
    public void handleBeforeCreate() {
        registeredAt = Instant.now();
        createdBy = SecurityUtil.getCurrentUserLogin().orElse("");
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        updatedAt = Instant.now();
        updatedBy = SecurityUtil.getCurrentUserLogin().orElse("");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return club.getName();
    }
}
