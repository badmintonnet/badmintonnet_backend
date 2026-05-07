package com.tlcn.sportsnet_backend.enums;

public enum ClubTournamentParticipantStatusEnum {
    DRAFT,             // Bản nháp, đang chọn roster
    PENDING,           // Chờ duyệt
    PAYMENT_REQUIRED,  // Đã đăng ký, cần thanh toán để xác nhận
    PAID,              // Đã thanh toán
    APPROVED,          // Đã được duyệt thi đấu
    REJECTED,          // Bị từ chối
    CANCELLED,         // CLB tự hủy đăng ký
    ELIMINATED         // Đã bị loại trong giải
}
