package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.config.VNPayConfig;
import com.tlcn.sportsnet_backend.dto.payment.VNPayCreateResponse;
import com.tlcn.sportsnet_backend.dto.payment.VNPayReturnResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import com.tlcn.sportsnet_backend.enums.ClubTournamentParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import com.tlcn.sportsnet_backend.util.VNPayUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TournamentPaymentService {

    private final VNPayConfig config;
    private final TournamentPaymentRepository paymentRepo;
    private final TournamentParticipantRepository participantRepo;
    private final TournamentTeamRepository teamRepo;
    private final AccountRepository accountRepo;
    private final TournamentCategoryRepository categoryRepo;
    private final ClubTournamentParticipantRepository clubParticipantRepo;
    private final AdminNotificationService adminNotificationService;

    @Value("${vnpay.client-ip:127.0.0.1}")
    private String vnpClientIp;

    private static final SimpleDateFormat VNPAY_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    // ========== INDIVIDUAL TOURNAMENT ==========

    public VNPayCreateResponse createPayment(String categoryId, Double amount) {
        Account account = getCurrentAccount();
        TournamentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new InvalidDataException("Category not found"));
        boolean isDouble = category.getCategory() != BadmintonCategoryEnum.MEN_SINGLE
                && category.getCategory() != BadmintonCategoryEnum.WOMEN_SINGLE;

        String txnRef = String.valueOf(System.currentTimeMillis());

        // Create payment record
        TournamentPayment payment = TournamentPayment.builder()
                .txnRef(txnRef)
                .amount(amount)
                .status(PaymentStatusEnum.PENDING)
                .build();
        if (isDouble) {
            TournamentTeam tournamentTeam = teamRepo.findByCategoryAndAccount(categoryId, account).orElse(null);
            payment.setTeam(tournamentTeam);
        } else {
            TournamentParticipant participant = participantRepo.findByAccountAndCategory(account, category);
            payment.setParticipant(participant);
        }
        paymentRepo.save(payment);

        // Build VNPay URL
        Map<String, String> params = buildVNPayParams(
                txnRef,
                amount,
                "Thanh toan le phi thi dau",
                vnpClientIp
        );
        return buildVNPayResponse(params);
    }

    // ========== CLUB TOURNAMENT ==========

    public VNPayCreateResponse createClubPayment(String participantId, Double amount) {
        Account account = getCurrentAccount();

        ClubTournamentParticipant clubParticipant = clubParticipantRepo.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Khong tim thay dang ky CLB"));

        // Verify caller is owner
        if (!clubParticipant.getClub().getOwner().getId().equals(account.getId())) {
            throw new InvalidDataException("Chi chu CLB moi co the thanh toan");
        }

        // Verify status
        if (clubParticipant.getStatus() != ClubTournamentParticipantStatusEnum.PENDING) {
            throw new InvalidDataException("Chi co the thanh toan khi trang thai PENDING");
        }

        Tournament tournament = clubParticipant.getTournament();
        Double paymentAmount = (amount != null) ? amount
                : (tournament.getClubRegistrationFee() != null
                        ? tournament.getClubRegistrationFee().doubleValue()
                        : 0.0);

        String txnRef = String.valueOf(System.currentTimeMillis());

        // Update status to PAYMENT_REQUIRED before redirecting to VNPay
        clubParticipant.setStatus(ClubTournamentParticipantStatusEnum.PAYMENT_REQUIRED);
        clubParticipantRepo.save(clubParticipant);

        // Create payment record
        TournamentPayment payment = TournamentPayment.builder()
                .txnRef(txnRef)
                .amount(paymentAmount)
                .status(PaymentStatusEnum.PENDING)
                .clubTournamentParticipant(clubParticipant)
                .build();
        paymentRepo.save(payment);

        // Build VNPay URL
        Map<String, String> params = buildVNPayParams(
                txnRef,
                paymentAmount,
                "Thanh toan phi dang ky CLB: " + tournament.getName(),
                vnpClientIp
        );
        return buildVNPayResponse(params);
    }

    // ========== VNPAY CALLBACK ==========

    public VNPayReturnResponse handleReturn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");

        TournamentPayment payment = paymentRepo.findByTxnRef(txnRef)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatusEnum.SUCCESS);
            payment.setPaidAt(Instant.now());
            payment.setVnpTransactionNo(transactionNo);
            payment.setBankCode(bankCode);
            // Update club participant to PAID
            if (payment.getClubTournamentParticipant() != null) {
                ClubTournamentParticipant cp = payment.getClubTournamentParticipant();
                cp.setStatus(ClubTournamentParticipantStatusEnum.PAID);
                clubParticipantRepo.save(cp);
            }
        } else {
            payment.setStatus(PaymentStatusEnum.FAILED);
            // Rollback club participant to PENDING
            if (payment.getClubTournamentParticipant() != null) {
                ClubTournamentParticipant cp = payment.getClubTournamentParticipant();
                cp.setStatus(ClubTournamentParticipantStatusEnum.PENDING);
                clubParticipantRepo.save(cp);
                adminNotificationService.notifyAllAdmins(
                        "Thanh toán giải đấu thất bại",
                        "CLB \"" + cp.getClub().getName() + "\" thanh toán thất bại cho giải \"" + cp.getTournament().getName() + "\". Mã lỗi VNPay: " + responseCode,
                        "/admin/tournaments"
                );
            } else {
                adminNotificationService.notifyAllAdmins(
                        "Thanh toán thất bại",
                        "Giao dịch " + txnRef + " thất bại. Mã lỗi VNPay: " + responseCode,
                        "/admin/tournaments"
                );
            }
        }

        paymentRepo.save(payment);

        // Build response
        VNPayReturnResponse.VNPayReturnResponseBuilder builder = VNPayReturnResponse.builder()
                .status(payment.getStatus().toString());

        if (payment.getClubTournamentParticipant() != null) {
            builder.tournamentId(payment.getClubTournamentParticipant().getTournament().getId())
                    .participantId(payment.getClubTournamentParticipant().getId())
                    .participationType(TournamentParticipationTypeEnum.CLUB);
        } else if (payment.getParticipant() != null) {
            builder.tournamentId(payment.getParticipant().getCategory().getTournament().getId())
                    .categoryId(payment.getParticipant().getCategory().getId())
                    .participationType(TournamentParticipationTypeEnum.INDIVIDUAL);
        } else if (payment.getTeam() != null) {
            builder.tournamentId(payment.getTeam().getCategory().getTournament().getId())
                    .categoryId(payment.getTeam().getCategory().getId())
                    .participationType(TournamentParticipationTypeEnum.INDIVIDUAL);
        }

        return builder.build();
    }

    // ========== PRIVATE HELPERS ==========

    private Account getCurrentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return accountRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
    }

    private Map<String, String> buildVNPayParams(String txnRef, Double amount, String orderInfo, String ipAddr) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", config.getTmnCode());
        params.put("vnp_Amount", String.valueOf(Math.round(amount * 100)));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_ReturnUrl", config.getReturnUrl());
        params.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(new Date()));
        return params;
    }

    private VNPayCreateResponse buildVNPayResponse(Map<String, String> params) {
        String query = VNPayUtil.buildQuery(params);
        String secureHash = VNPayUtil.hmacSHA512(config.getHashSecret(), query);
        return VNPayCreateResponse.builder()
                .paymentUrl(config.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash)
                .build();
    }
}
