package com.tlcn.sportsnet_backend.dto.account;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountFriend {
    String id;
    String fullName;
    String avatarUrl;
    String skillLevel;
    String slug;
    Long mutualFriends;
    String address;
}
