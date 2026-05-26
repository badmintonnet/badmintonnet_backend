package com.tlcn.sportsnet_backend.enums;

import lombok.Getter;

/**
 * Loại "ván" (rubber) trong một tie giữa 2 CLB.
 * Một tournament CLUB cấu hình teamMatchFormat → sinh ra N rubber với mỗi loại bên dưới.
 */
@Getter
public enum ClubLineTypeEnum {
    SINGLES("Đơn", 1),
    MEN_DOUBLES("Đôi nam", 2),
    WOMEN_DOUBLES("Đôi nữ", 2),
    MIXED_DOUBLES("Đôi nam-nữ", 2);

    private final String label;
    /** Số người cần cho 1 rubber */
    private final int playersRequired;

    ClubLineTypeEnum(String label, int playersRequired) {
        this.label = label;
        this.playersRequired = playersRequired;
    }
}
