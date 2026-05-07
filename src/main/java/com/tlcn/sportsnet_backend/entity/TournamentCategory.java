package com.tlcn.sportsnet_backend.entity;

import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import com.tlcn.sportsnet_backend.util.SlugUtil;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Enumerated(EnumType.STRING)
    BadmintonCategoryEnum category;

    Double minLevel;
    Double maxLevel;
    Integer maxParticipants;

    BigDecimal registrationFee; // Lệ phí tham gia

    @Column(columnDefinition = "TEXT")
    String description; // Mô tả hạng mục

    @Column(name = "rule", columnDefinition = "TEXT")
    String rules; // Thể lệ thi đấu

    String firstPrize;

    String secondPrize;

    String thirdPrize;

    @ManyToOne
    @JoinColumn(name = "tournament_id")
    Tournament tournament;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<TournamentParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<TournamentTeam> teams = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<TournamentResult> results = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<TournamentMatch> matches = new ArrayList<>();

    String slug;

    @PrePersist
    public void handleBeforeCreate(){
        if (this.slug == null || this.slug.isBlank()) {
            String slug = SlugUtil.toSlug(this.category.getLabel());
            String randomSuffix = SlugUtil.randomString(8);
            this.slug = slug + "-" + randomSuffix;
        }
    }
}

