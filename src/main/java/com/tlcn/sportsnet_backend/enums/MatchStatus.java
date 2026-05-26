package com.tlcn.sportsnet_backend.enums;

public enum MatchStatus {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED,
    CANCELLED,
    SKIPPED   // Auto-skipped khi tie đã có winner (CLB đối phương đạt majority rubber)
}
