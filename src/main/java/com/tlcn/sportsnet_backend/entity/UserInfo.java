package com.tlcn.sportsnet_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tlcn.sportsnet_backend.util.SecurityUtil;
import com.tlcn.sportsnet_backend.util.SlugUtil;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "user_info")
public class UserInfo {
    @Id
    String id;
    @Column(unique = true, nullable = false)
    private String slug;
    String fullName;
    LocalDate birthDate;
    String gender;
    String address;
    Double latitude;
    Double longitude;
    String bio;
    String avatarUrl;
    String phone;
    boolean isProfileProtected = true;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    @PrePersist
    public void handleBeforeCreate(){

        if (this.slug == null || this.slug.isBlank()) {
            String slug = SlugUtil.toSlug(this.fullName);
            String randomSuffix = SlugUtil.randomString(8);
            this.slug = slug + "-" + randomSuffix;
        }
    }

    @PreUpdate
    public void handleBeforeUpdate(){
        if (this.slug == null || this.slug.isBlank()) {
            String slug = SlugUtil.toSlug(this.fullName);
            String randomSuffix = SlugUtil.randomString(8);
            this.slug = slug + "-" + randomSuffix;
        }
    }
}
