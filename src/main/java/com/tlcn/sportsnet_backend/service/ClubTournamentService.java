package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.club_tournament.*;
import com.tlcn.sportsnet_backend.dto.tournament.TeamMatchFormatDTO;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.*;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.*;
import com.tlcn.sportsnet_backend.service.helper.ClubLineupHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClubTournamentService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final ClubTournamentParticipantRepository clubTournamentParticipantRepository;
    private final ClubTournamentRosterRepository clubTournamentRosterRepository;
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final AccountRepository accountRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AdminNotificationService adminNotificationService;

    // =========================================================
    // 1. ĐĂNG KÝ CLB THAM GIA TOURNAMENT
    // =========================================================

    @Transactional
    public ClubTournamentParticipantResponse registerClub(String tournamentId, ClubTournamentRegistrationRequest request) {
        // Validate request null
        if (request == null) {
            throw new InvalidDataException("Request khong duoc de trong");
        }
        if (request.getClubId() == null || request.getClubId().isBlank()) {
            throw new InvalidDataException("clubId khong duoc de trong");
        }

        List<String> rosterIds = request.getRosterAccountIds();
        if (rosterIds == null || rosterIds.isEmpty()) {
            throw new InvalidDataException("rosterAccountIds khong duoc de trong");
        }

        Account owner = getCurrentAccount();

        Club club = clubRepository.findByIdWithOwner(request.getClubId())
                .orElseThrow(() -> new InvalidDataException("Khong tim thay CLB"));

        // Kiểm tra quyền: chỉ OWNER mới được đăng ký
        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể đăng ký tham gia tournament");
        }

        // Kiểm tra CLB đang hoạt động
        if (club.getStatus() != ClubStatusEnum.ACTIVE) {
            throw new InvalidDataException("CLB phải ở trạng thái ACTIVE mới có thể đăng ký tournament");
        }

        Tournament tournament = tournamentRepository.findByIdForClubTournament(tournamentId)
                .orElseThrow(() -> new InvalidDataException("Khong tim thay giai dau"));

        // Kiểm tra tournament phải là loại CLUB
        if (tournament.getParticipationType() != TournamentParticipationTypeEnum.CLUB) {
            throw new InvalidDataException("Tournament này không dành cho CLB đăng ký");
        }

        // Kiểm tra thời gian đăng ký
        validateRegistrationDate(tournament);

        // Kiểm tra CLB đã đăng ký tournament này chưa
        if (clubTournamentParticipantRepository.existsByClubAndTournament(club, tournament)) {
            throw new InvalidDataException("CLB đã đăng ký giải đấu này rồi");
        }

        // Kiểm tra số lượng CLB đã đăng ký
        int currentCount = clubTournamentParticipantRepository.countByTournament(tournament);
        if (tournament.getMaxClubs() != null && currentCount >= tournament.getMaxClubs()) {
            throw new InvalidDataException("Giải đấu đã đủ số lượng CLB tham gia");
        }

        // Validate danh sách roster
        validateRosterSize(tournament, rosterIds);
        List<ClubMember> rosterMembers = validateRosterMembersByAccountIds(club, tournament, rosterIds);

        // Tạo ClubTournamentParticipant
        ClubTournamentParticipant participant = ClubTournamentParticipant.builder()
                .club(club)
                .tournament(tournament)
                .status(ClubTournamentParticipantStatusEnum.PENDING)
                .build();
        clubTournamentParticipantRepository.save(participant);

        // Tạo roster entries
        List<ClubTournamentRoster> rosterEntries = new ArrayList<>();
        for (ClubMember member : rosterMembers) {
            rosterEntries.add(ClubTournamentRoster.builder()
                    .clubTournamentParticipant(participant)
                    .clubMember(member)
                    .canModify(true)
                    .build());
        }
        clubTournamentRosterRepository.saveAll(rosterEntries);
        participant.setRoster(rosterEntries);

        // Build response - fetch data eagerly to avoid lazy loading issues
        ClubTournamentParticipantResponse response = buildFullResponse(participant);

        adminNotificationService.notifyAllAdmins(
                "CLB đăng ký tham gia giải đấu",
                "CLB \"" + club.getName() + "\" vừa đăng ký tham gia giải đấu \"" + tournament.getName() + "\". Trạng thái: PENDING.",
                "/admin/tournaments"
        );

        return response;
    }

    // =========================================================
    // 2. CẬP NHẬT ROSTER (trước deadline)
    // =========================================================

    @Transactional
    public ClubTournamentParticipantResponse updateRoster(String participantId, UpdateRosterRequest request) {
        if (request == null) {
            throw new InvalidDataException("Request is required");
        }

        // Support both rosterAccountIds and rosterMemberIds
        List<String> rosterIds = request.getRosterAccountIds();
        if (rosterIds == null || rosterIds.isEmpty()) {
            rosterIds = request.getRosterMemberIds();
        }
        if (rosterIds == null || rosterIds.isEmpty()) {
            throw new InvalidDataException("rosterAccountIds is required");
        }

        Account owner = getCurrentAccount();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        Club club = participant.getClub();

        // Kiểm tra quyền
        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể cập nhật roster");
        }

        // Chỉ cho phép cập nhật khi status là PENDING hoặc DRAFT, PAYMENT_REQUIRED
        if (participant.getStatus() == ClubTournamentParticipantStatusEnum.APPROVED
                || participant.getStatus() == ClubTournamentParticipantStatusEnum.CANCELLED
                || participant.getStatus() == ClubTournamentParticipantStatusEnum.ELIMINATED
                || participant.getStatus() == ClubTournamentParticipantStatusEnum.REJECTED) {
            throw new InvalidDataException("Không thể cập nhật roster ở trạng thái " + participant.getStatus());
        }

        Tournament tournament = participant.getTournament();

        // Kiểm tra còn trong thời gian đăng ký
        validateRegistrationDate(tournament);

        // Validate roster mới
        validateRosterSize(tournament, rosterIds);
        List<ClubMember> rosterMembers = validateRosterMembersByAccountIds(club, tournament, rosterIds);

        // Xóa roster cũ, tạo roster mới
        List<ClubTournamentRoster> oldRoster = clubTournamentRosterRepository.findByClubTournamentParticipant(participant);
        clubTournamentRosterRepository.deleteAll(oldRoster);

        List<ClubTournamentRoster> newRoster = new ArrayList<>();
        for (ClubMember member : rosterMembers) {
            newRoster.add(ClubTournamentRoster.builder()
                    .clubTournamentParticipant(participant)
                    .clubMember(member)
                    .canModify(true)
                    .build());
        }
        clubTournamentRosterRepository.saveAll(newRoster);
        participant.setRoster(newRoster);

        return toResponse(participant, false);
    }

    // =========================================================
    // 3. HỦY ĐĂNG KÝ
    // =========================================================

    @Transactional
    public void cancelRegistration(String participantId) {
        Account owner = getCurrentAccount();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(participant.getClub(), owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể hủy đăng ký");
        }

        if (participant.getStatus() == ClubTournamentParticipantStatusEnum.ELIMINATED
                || participant.getStatus() == ClubTournamentParticipantStatusEnum.CANCELLED) {
            throw new InvalidDataException("Đăng ký đã ở trạng thái không thể hủy");
        }

        participant.setStatus(ClubTournamentParticipantStatusEnum.CANCELLED);
        clubTournamentParticipantRepository.save(participant);
    }

    // =========================================================
    // 4. ADMIN: DUYỆT / TỪ CHỐI CLB
    // =========================================================

    @Transactional
    public void approveClubParticipant(String participantId) {
        // 1. Validate admin
        validateAdmin();

        // 2. Load participant với eager fetch
        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        // 3. Kiểm tra status hợp lệ (chỉ PAID mới được duyệt - đã thanh toán rồi)
        if (participant.getStatus() != ClubTournamentParticipantStatusEnum.PAID) {
            throw new InvalidDataException("CLB chưa thanh toán, không thể duyệt");
        }

        // 4. Kiểm tra club còn ACTIVE
        Club club = participant.getClub();
        if (club.getStatus() != ClubStatusEnum.ACTIVE) {
            throw new InvalidDataException("CLB không còn ở trạng thái ACTIVE");
        }

        // 5. Kiểm tra tournament chưa start
        Tournament tournament = participant.getTournament();
        if (tournament.getStartDate() != null && LocalDateTime.now().isAfter(tournament.getStartDate())) {
            throw new InvalidDataException("Giải đấu đã bắt đầu, không thể duyệt thêm CLB");
        }

        // 6. Kiểm tra capacity (số CLB đã approve < maxClubs)
        int approvedCount = (int) clubTournamentParticipantRepository.findByTournamentIdAndStatus(
                tournament.getId(), ClubTournamentParticipantStatusEnum.APPROVED).size();
        if (tournament.getMaxClubs() != null && approvedCount >= tournament.getMaxClubs()) {
            throw new InvalidDataException("Giải đấu đã đủ số lượng CLB được duyệt");
        }

        // 7. Approve
        participant.setStatus(ClubTournamentParticipantStatusEnum.APPROVED);
        clubTournamentParticipantRepository.save(participant);

        // 8. Gửi notification cho owner
        notificationService.sendToAccount(
                club.getOwner().getEmail(),
                "CLB được duyệt tham gia tournament",
                String.format("CLB %s đã được duyệt tham gia giải đấu %s",
                        club.getName(), tournament.getName()),
                "/tournament/" + tournament.getSlug()
        );

        // 9. Gửi notification cho các thành viên trong roster
        List<ClubTournamentRoster> roster = participant.getRoster();
        if (roster != null) {
            for (ClubTournamentRoster entry : roster) {
                notificationService.sendToAccount(
                        entry.getClubMember().getAccount().getEmail(),
                        "Thông báo tham gia tournament",
                        String.format("CLB %s của bạn đã được duyệt tham gia giải đấu %s",
                                club.getName(), tournament.getName()),
                        "/tournament/" + tournament.getSlug()
                );
            }
        }
    }

    @Transactional
    public void rejectClubParticipant(String participantId) {
        validateAdmin();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        participant.setStatus(ClubTournamentParticipantStatusEnum.REJECTED);
        clubTournamentParticipantRepository.save(participant);

        notificationService.sendToAccount(
                participant.getClub().getOwner().getEmail(),
                "CLB bị từ chối tham gia tournament",
                String.format("CLB %s đã bị từ chối tham gia giải đấu %s",
                        participant.getClub().getName(),
                        participant.getTournament().getName()),
                "/tournament/" + participant.getTournament().getSlug()
        );
    }

    // =========================================================
    // 5. QUERY: DANH SÁCH CLB TRONG TOURNAMENT
    // =========================================================

    public PagedResponse<ClubTournamentParticipantResponse> getAllClubParticipants(
            String tournamentId,
            List<ClubTournamentParticipantStatusEnum> statuses,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("registeredAt").descending());

        Page<ClubTournamentParticipant> participantPage;
        if (statuses == null || statuses.isEmpty()) {
            participantPage = clubTournamentParticipantRepository.findByTournamentId(tournamentId, pageable);
        } else {
            participantPage = clubTournamentParticipantRepository.findByTournamentIdAndStatusIn(tournamentId, statuses, pageable);
        }

        List<ClubTournamentParticipantResponse> content = participantPage.getContent()
                .stream()
                .map(p -> toResponse(p, false))
                .toList();

        return new PagedResponse<>(content, participantPage.getNumber(), participantPage.getSize(),
                participantPage.getTotalElements(), participantPage.getTotalPages(), participantPage.isLast());
    }

    // =========================================================
    // 6. QUERY: TOURNAMENTS CỦA CLB (My Club - Tab Giải đấu)
    // =========================================================

    public List<ClubTournamentParticipantResponse> getMyClubTournaments(String clubId) {
        Account account = getCurrentAccount();

        // Verify club exists
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new InvalidDataException("Khong tim thay CLB"));

        // Verify caller is club owner
        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, account);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chi chu CLB moi co the xem danh sach");
        }

        // Get all tournaments for this club
        List<ClubTournamentParticipant> participants = clubTournamentParticipantRepository
                .findByClubIdWithDetails(clubId);

        return participants.stream()
                .map(p -> toResponse(p, false))  // false = basic info, no full roster
                .toList();
    }

    // QUERY: Chi tiết đăng ký của một CLB (dùng cho admin hoặc club owner)
    public ClubTournamentParticipantResponse getClubParticipantDetail(String participantId) {
        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));
        return toResponse(participant, true);
    }

    // QUERY: CLB của người dùng hiện tại đã đăng ký tournament này chưa
    public ClubTournamentParticipantResponse getMyClubParticipation(String tournamentId, String clubId) {
        Account account = getCurrentAccount();
        Club club = clubRepository.findByIdWithOwner(clubId)
                .orElseThrow(() -> new InvalidDataException("Khong tim thay CLB"));

        // Chỉ owner mới được xem
        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, account);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể xem thông tin đăng ký");
        }

        Tournament tournament = tournamentRepository.findByIdForClubTournament(tournamentId)
                .orElseThrow(() -> new InvalidDataException("Khong tim thay giai dau"));

        return clubTournamentParticipantRepository.findByClubAndTournament(club, tournament)
                .map(p -> toResponse(p, true))
                .orElse(null);
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private void validateRegistrationDate(Tournament tournament) {
        LocalDateTime now = LocalDateTime.now();
        // Use registrationEndDate from tournament (common for both individual and club)
        LocalDateTime deadline = tournament.getRegistrationEndDate();

        if (tournament.getRegistrationStartDate() != null && now.isBefore(tournament.getRegistrationStartDate())) {
            throw new InvalidDataException("Giải đấu chưa mở đăng ký");
        }
        if (deadline != null && now.isAfter(deadline)) {
            throw new InvalidDataException("Giải đấu đã hết thời gian đăng ký");
        }
    }

    private void validateRosterSize(Tournament tournament, List<String> rosterIds) {
        if (rosterIds == null || rosterIds.isEmpty()) {
            throw new InvalidDataException("Danh sách roster không được để trống");
        }
        // Check for duplicates
        if (rosterIds.size() != rosterIds.stream().distinct().count()) {
            throw new InvalidDataException("Danh sách roster không được chứa trùng lặp");
        }
        if (tournament.getMinClubRosterSize() != null && rosterIds.size() < tournament.getMinClubRosterSize()) {
            throw new InvalidDataException("Roster cần ít nhất " + tournament.getMinClubRosterSize() + " thành viên");
        }
        if (tournament.getMaxClubRosterSize() != null && rosterIds.size() > tournament.getMaxClubRosterSize()) {
            throw new InvalidDataException("Roster không được vượt quá " + tournament.getMaxClubRosterSize() + " thành viên");
        }
    }

    /**
     * Validate roster members by account IDs.
     * rosterIds now accepts account IDs (not clubMemberId)
     */
    private List<ClubMember> validateRosterMembersByAccountIds(Club club, Tournament tournament, List<String> accountIds) {
        // Fetch all club members with account info once to avoid N+1
        List<ClubMember> allClubMembers = clubMemberRepository.findByClubIdWithAccount(club.getId());

        List<ClubMember> members = new ArrayList<>();
        for (String accountId : accountIds) {
            // Find ClubMember by account ID from pre-fetched list
            ClubMember member = allClubMembers.stream()
                    .filter(m -> m.getAccount().getId().equals(accountId))
                    .findFirst()
                    .orElse(null);

            if (member == null) {
                throw new InvalidDataException("Khong tim thay thanh vien voi account ID: " + accountId + " trong CLB nay");
            }

            // Phai co trang thai APPROVED
            if (member.getStatus() != ClubMemberStatusEnum.APPROVED) {
                throw new InvalidDataException("Thanh vien " + member.getAccount().getUserInfo().getFullName() + " chua duoc duyet vao CLB");
            }

            members.add(member);
        }
        return members;
    }

    private double parseSkillLevel(String skillLevel) {
        try {
            return Double.parseDouble(skillLevel);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void validateAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy tài khoản"));
        boolean isAdmin = account.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new InvalidDataException("Chỉ admin mới có quyền thực hiện thao tác này");
        }
    }

    private Account getCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy tài khoản"));
    }

    // Build response với full roster (dùng trong registerClub)
    private ClubTournamentParticipantResponse buildResponse(ClubTournamentParticipant participant) {
        // Roster đã được set từ trước khi gọi method này
        List<ClubTournamentRoster> rosterList = participant.getRoster();
        List<ClubRosterMemberResponse> rosterResponses = buildRosterResponses(rosterList);
        return buildBaseResponse(participant, rosterResponses);
    }

    // Response đầy đủ (bao gồm full roster) - dùng cho detail
    private ClubTournamentParticipantResponse buildFullResponse(ClubTournamentParticipant participant) {
        List<ClubTournamentRoster> rosterList = participant.getRoster();
        if (rosterList == null || rosterList.isEmpty()) {
            rosterList = clubTournamentRosterRepository.findByClubTournamentParticipant(participant);
        }
        List<ClubRosterMemberResponse> rosterResponses = buildRosterResponses(rosterList);
        return buildBaseResponse(participant, rosterResponses);
    }

    // Response cơ bản (không bao gồm roster chi tiết) - dùng cho danh sách phân trang
    private ClubTournamentParticipantResponse buildBasicResponse(ClubTournamentParticipant participant) {
        Club club = participant.getClub();
        Tournament tournament = participant.getTournament();
        List<ClubTournamentRoster> rosterList = participant.getRoster();
        int rosterSize = rosterList != null ? rosterList.size() : 0;
        List<ClubRosterMemberResponse> rosterResponses = buildRosterResponses(rosterList);
        return ClubTournamentParticipantResponse.builder()
                .id(participant.getId())
                .clubId(club.getId())
                .clubName(club.getName())
                .clubLogoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .clubSlug(club.getSlug())
                .clubLocation(club.getLocation())
                .ownerName(club.getOwner().getUserInfo().getFullName())
                .ownerEmail(club.getOwner().getEmail())
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .tournamentSlug(tournament.getSlug())
                .tournamentStatus(tournament.getStatus())
                .status(participant.getStatus())
                .registeredAt(participant.getRegisteredAt())
                .paid(isPaid(participant.getStatus()))
                .roster(rosterResponses)
                .rosterSize(rosterSize)
                .build();
    }

    // Shared base builder - tránh duplicate code
    private ClubTournamentParticipantResponse buildBaseResponse(ClubTournamentParticipant participant,
                                                                 List<ClubRosterMemberResponse> rosterResponses) {
        Club club = participant.getClub();
        Tournament tournament = participant.getTournament();
        return ClubTournamentParticipantResponse.builder()
                .id(participant.getId())
                .clubId(club.getId())
                .clubName(club.getName())
                .clubLogoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .clubSlug(club.getSlug())
                .clubLocation(club.getLocation())
                .ownerName(club.getOwner().getUserInfo().getFullName())
                .ownerEmail(club.getOwner().getEmail())
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .tournamentSlug(tournament.getSlug())
                .tournamentStatus(tournament.getStatus())
                .status(participant.getStatus())
                .registeredAt(participant.getRegisteredAt())
                .paid(isPaid(participant.getStatus()))
                .roster(rosterResponses)
                .rosterSize(rosterResponses != null ? rosterResponses.size() : 0)
                .build();
    }

    /** Tính trạng thái đã thanh toán từ status (entity không có field paid riêng) */
    private boolean isPaid(ClubTournamentParticipantStatusEnum status) {
        return status == ClubTournamentParticipantStatusEnum.PAID
                || status == ClubTournamentParticipantStatusEnum.APPROVED
                || status == ClubTournamentParticipantStatusEnum.ELIMINATED;
    }

    // Helper: Build roster member responses
    private List<ClubRosterMemberResponse> buildRosterResponses(List<ClubTournamentRoster> rosterList) {
        List<ClubRosterMemberResponse> rosterResponses = new ArrayList<>();
        if (rosterList != null) {
            for (ClubTournamentRoster entry : rosterList) {
                ClubMember member = entry.getClubMember();
                Account account = member.getAccount();
                String skillLevel = playerRatingRepository.findByAccount(account)
                        .map(PlayerRating::getSkillLevel)
                        .orElse("Chua co");

                rosterResponses.add(ClubRosterMemberResponse.builder()
                        .rosterEntryId(entry.getId())
                        .clubMemberId(member.getId())
                        .accountId(account.getId())
                        .fullName(account.getUserInfo().getFullName())
                        .email(account.getEmail())
                        .avatarUrl(fileStorageService.getFileUrl(account.getUserInfo().getAvatarUrl(), "/avatar"))
                        .slug(account.getUserInfo().getSlug())
                        .skillLevel(skillLevel)
                        .role(member.getRole().name())
                        .position(entry.getPosition())
                        .canModify(entry.getCanModify())
                        .build());
            }
        }
        return rosterResponses;
    }

    private ClubTournamentParticipantResponse toResponse(ClubTournamentParticipant participant, boolean includeFullRoster) {
        if (includeFullRoster) {
            return buildFullResponse(participant);
        } else {
            return buildBasicResponse(participant);
        }
    }

    // =========================================================
    // 7. CHỌN ĐẠI DIỆN ĐƠN NAM
    // =========================================================

    /**
     * Backward-compat: chọn đại diện đơn (1 SINGLES). Tương đương setLineup với
     * lineup chỉ có 1 entry "SINGLES_1".
     */
    @Transactional
    public void setRepresentative(String participantId, String rosterEntryId) {
        ClubLineupRequest req = ClubLineupRequest.builder()
                .lineup(Map.of("SINGLES_1", rosterEntryId))
                .build();
        setLineup(participantId, req);
    }

    /**
     * Set / update lineup cho participant. Cho phép submit partial.
     * - Validate roster entries thuộc về participant
     * - Validate constraint: 1 người tối đa 2 rubber/tie
     * - Validate position hợp lệ với teamMatchFormat
     * - Bị khoá nếu bracket đã được generate (tournament IN_PROGRESS hoặc COMPLETED)
     */
    @Transactional
    public ClubLineupResponse setLineup(String participantId, ClubLineupRequest request) {
        if (request == null || request.getLineup() == null) {
            throw new InvalidDataException("Lineup không được để trống");
        }
        Account owner = getCurrentAccount();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        Club club = participant.getClub();

        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể chọn lineup");
        }

        ClubTournamentParticipantStatusEnum status = participant.getStatus();
        if (status != ClubTournamentParticipantStatusEnum.PAID
                && status != ClubTournamentParticipantStatusEnum.APPROVED) {
            throw new InvalidDataException("Chỉ có thể chọn lineup khi CLB đã thanh toán hoặc đã được duyệt");
        }

        Tournament tournament = participant.getTournament();
        if (tournament.getStatus() != null
                && tournament.getStatus() != TournamentStatus.UPCOMING
                && tournament.getStatus() != TournamentStatus.REGISTRATION_OPEN
                && tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED) {
            throw new InvalidDataException("Lineup đã bị khoá khi giải đấu đã bắt đầu");
        }

        TeamMatchFormatDTO format = ClubLineupHelper.parseFormat(tournament.getTeamMatchFormat());
        List<ClubLineupHelper.PositionSpec> validPositions = ClubLineupHelper.buildPositions(format);
        Map<String, ClubLineupHelper.PositionSpec> validPosMap = new HashMap<>();
        for (ClubLineupHelper.PositionSpec p : validPositions) validPosMap.put(p.position(), p);

        List<ClubTournamentRoster> allRoster = clubTournamentRosterRepository
                .findByClubTournamentParticipant(participant);
        Map<String, ClubTournamentRoster> rosterById = new HashMap<>();
        for (ClubTournamentRoster r : allRoster) rosterById.put(r.getId(), r);

        /** Merge patch: chỉ cập nhật các position có trong body; không xoá chỗ không gửi (draft partial). */
        Map<String, String> finalAssignment = new LinkedHashMap<>();
        if (!request.getLineup().isEmpty()) {
            for (ClubTournamentRoster r : allRoster) {
                if (r.getPosition() != null && !r.getPosition().isBlank()) {
                    finalAssignment.put(r.getPosition(), r.getId());
                }
            }
            for (String pos : request.getLineup().keySet()) {
                if (!validPosMap.containsKey(pos)) {
                    throw new InvalidDataException("Vị trí không hợp lệ với format giải: " + pos);
                }
            }
            for (Map.Entry<String, String> e : request.getLineup().entrySet()) {
                String pos = e.getKey();
                String rosterEntryId = e.getValue();
                if (rosterEntryId == null || rosterEntryId.isBlank()) {
                    finalAssignment.remove(pos);
                    continue;
                }
                ClubTournamentRoster entry = rosterById.get(rosterEntryId);
                if (entry == null) {
                    throw new InvalidDataException("Roster entry không thuộc CLB này: " + rosterEntryId);
                }
                finalAssignment.put(pos, rosterEntryId);
            }
        } else {
            for (ClubTournamentRoster r : allRoster) {
                if (r.getPosition() != null && !r.getPosition().isBlank()) {
                    finalAssignment.put(r.getPosition(), r.getId());
                }
            }
        }

        /** Một roster entry chỉ được gán đúng 1 position tại một thời điểm. */
        Map<String, Long> entryUseCount =
                finalAssignment.values().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        v -> v, java.util.stream.Collectors.counting()));
        for (Map.Entry<String, Long> ue : entryUseCount.entrySet()) {
            if (ue.getValue() > 1) {
                ClubTournamentRoster r = rosterById.get(ue.getKey());
                String name = r != null ? r.getClubMember().getAccount().getUserInfo().getFullName() : ue.getKey();
                throw new InvalidDataException(
                        "Mỗi thành viên chỉ có thể gán vào một vị trí: " + name);
            }
        }

        // Validate: 1 người tối đa 2 rubber/tie (theo các rubber được gán trong lineup)
        Map<String, java.util.Set<String>> playerRubbers = new HashMap<>();
        for (Map.Entry<String, String> e : finalAssignment.entrySet()) {
            ClubLineupHelper.PositionSpec spec = validPosMap.get(e.getKey());
            String rubberKey = spec.lineType().name() + "_" + spec.lineIndex();
            playerRubbers.computeIfAbsent(e.getValue(), k -> new java.util.HashSet<>()).add(rubberKey);
        }
        for (Map.Entry<String, java.util.Set<String>> e : playerRubbers.entrySet()) {
            if (e.getValue().size() > 2) {
                ClubTournamentRoster r = rosterById.get(e.getKey());
                String name = r != null ? r.getClubMember().getAccount().getUserInfo().getFullName() : e.getKey();
                throw new InvalidDataException("Thành viên " + name + " được xếp quá 2 ván trong tie");
            }
        }

        // Validate: trong cùng rubber doubles, 2 slot không được trùng người
        for (ClubLineupHelper.PositionSpec spec : validPositions) {
            if (spec.playerSlot() == null || spec.playerSlot() != 1) continue;
            String pos1 = spec.position();
            String pos2 = ClubLineupHelper.formatPosition(spec.lineType(), spec.lineIndex(), 2);
            String r1 = finalAssignment.get(pos1);
            String r2 = finalAssignment.get(pos2);
            if (r1 != null && r1.equals(r2)) {
                throw new InvalidDataException("Hai người trong cùng ván đôi không được trùng nhau: "
                        + spec.lineType().getLabel() + " #" + spec.lineIndex());
            }
        }

        // Apply: clear tất cả position, set lại theo finalAssignment
        for (ClubTournamentRoster r : allRoster) {
            r.setPosition(null);
        }
        for (Map.Entry<String, String> e : finalAssignment.entrySet()) {
            ClubTournamentRoster entry = rosterById.get(e.getValue());
            entry.setPosition(e.getKey());
        }
        clubTournamentRosterRepository.saveAll(allRoster);

        return buildLineupResponse(participant, allRoster, format, validPositions);
    }

    /**
     * Lấy lineup hiện tại + thông tin format.
     */
    public ClubLineupResponse getLineup(String participantId) {
        Account owner = getCurrentAccount();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        Club club = participant.getClub();

        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể xem lineup");
        }

        Tournament tournament = participant.getTournament();
        TeamMatchFormatDTO format = ClubLineupHelper.parseFormat(tournament.getTeamMatchFormat());
        List<ClubLineupHelper.PositionSpec> positions = ClubLineupHelper.buildPositions(format);

        List<ClubTournamentRoster> roster = clubTournamentRosterRepository
                .findByClubTournamentParticipant(participant);

        return buildLineupResponse(participant, roster, format, positions);
    }

    /**
     * Backward-compat: getRepresentative trả về SINGLES_1 player như API cũ.
     */
    public ClubMatchParticipantResponse getRepresentative(String participantId) {
        Account owner = getCurrentAccount();

        ClubTournamentParticipant participant = clubTournamentParticipantRepository.findByIdWithDetails(participantId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy đăng ký CLB"));

        Club club = participant.getClub();

        ClubMember ownerMember = clubMemberRepository.findByClubAndAccountWithAccount(club, owner);
        if (ownerMember == null || ownerMember.getRole() != ClubMemberRoleEnum.OWNER) {
            throw new InvalidDataException("Chỉ chủ CLB mới có thể xem đại diện");
        }

        // Tìm theo position mới SINGLES_1 trước, fallback "SINGLES" (legacy)
        Optional<ClubTournamentRoster> rep = clubTournamentRosterRepository
                .findByClubTournamentParticipant_IdAndPositionWithDetails(participantId, "SINGLES_1");
        if (rep.isEmpty()) {
            rep = clubTournamentRosterRepository
                    .findByClubTournamentParticipant_IdAndPositionWithDetails(participantId, "SINGLES");
        }
        if (rep.isEmpty()) return null;

        ClubTournamentRoster r = rep.get();
        ClubMember cm = r.getClubMember();
        Account acc = cm.getAccount();

        return ClubMatchParticipantResponse.builder()
                .participantId(participantId)
                .clubId(club.getId())
                .clubName(club.getName())
                .clubLogoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .memberId(acc.getId())
                .memberName(acc.getUserInfo().getFullName())
                .memberAvatarUrl(fileStorageService.getFileUrl(acc.getUserInfo().getAvatarUrl(), "/avatar"))
                .build();
    }

    private ClubLineupResponse buildLineupResponse(
            ClubTournamentParticipant participant,
            List<ClubTournamentRoster> roster,
            TeamMatchFormatDTO format,
            List<ClubLineupHelper.PositionSpec> positions) {
        Map<String, ClubTournamentRoster> byPosition = new HashMap<>();
        for (ClubTournamentRoster r : roster) {
            if (r.getPosition() != null) byPosition.put(r.getPosition(), r);
        }

        List<ClubLineupSlotResponse> slots = new ArrayList<>();
        int filled = 0;
        for (ClubLineupHelper.PositionSpec spec : positions) {
            ClubTournamentRoster entry = byPosition.get(spec.position());
            ClubLineupSlotResponse.ClubLineupSlotResponseBuilder b = ClubLineupSlotResponse.builder()
                    .position(spec.position())
                    .lineType(spec.lineType())
                    .lineIndex(spec.lineIndex())
                    .playerSlot(spec.playerSlot());
            if (entry != null) {
                Account acc = entry.getClubMember().getAccount();
                b.rosterEntryId(entry.getId())
                        .accountId(acc.getId())
                        .fullName(acc.getUserInfo().getFullName())
                        .avatarUrl(fileStorageService.getFileUrl(acc.getUserInfo().getAvatarUrl(), "/avatar"));
                filled++;
            }
            slots.add(b.build());
        }

        Tournament tournament = participant.getTournament();
        boolean locked = tournament.getStatus() != null
                && tournament.getStatus() != TournamentStatus.UPCOMING
                && tournament.getStatus() != TournamentStatus.REGISTRATION_OPEN
                && tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED;

        return ClubLineupResponse.builder()
                .participantId(participant.getId())
                .clubId(participant.getClub().getId())
                .clubName(participant.getClub().getName())
                .format(format)
                .slots(slots)
                .filledCount(filled)
                .totalSlots(positions.size())
                .complete(filled == positions.size() && positions.size() > 0)
                .locked(locked)
                .build();
    }

    // =========================================================
    // 9. ADMIN: TẠO BẢNG ĐẤU CLB
    // =========================================================

    @Transactional
    public ClubBracketResponse generateClubBracket(String tournamentId) {
        validateAdmin();

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy giải đấu"));

        if (tournament.getParticipationType() != TournamentParticipationTypeEnum.CLUB) {
            throw new InvalidDataException("Giải đấu không phải loại CLUB");
        }

        List<ClubTournamentParticipant> approved = clubTournamentParticipantRepository
                .findByTournamentIdAndStatus(tournamentId, ClubTournamentParticipantStatusEnum.APPROVED);

        if (approved.size() < 2) {
            throw new InvalidDataException("Cần ít nhất 2 CLB đã duyệt để tạo bảng đấu");
        }

        // Parse format + sinh danh sách positions cần kiểm tra
        TeamMatchFormatDTO format = ClubLineupHelper.parseFormat(tournament.getTeamMatchFormat());
        List<ClubLineupHelper.PositionSpec> positionSpecs = ClubLineupHelper.buildPositions(format);
        if (positionSpecs.isEmpty()) {
            throw new InvalidDataException("Tournament chưa có cấu hình teamMatchFormat hợp lệ");
        }

        // Validate: mọi CLB approved phải có đủ lineup
        Map<String, Map<String, ClubTournamentRoster>> lineupByClub = new LinkedHashMap<>();
        for (ClubTournamentParticipant p : approved) {
            List<ClubTournamentRoster> roster = clubTournamentRosterRepository
                    .findByClubTournamentParticipant(p);
            Map<String, ClubTournamentRoster> byPos = new HashMap<>();
            for (ClubTournamentRoster r : roster) {
                if (r.getPosition() != null) byPos.put(r.getPosition(), r);
            }
            for (ClubLineupHelper.PositionSpec spec : positionSpecs) {
                if (!byPos.containsKey(spec.position())) {
                    throw new InvalidDataException(
                            "CLB \"" + p.getClub().getName() + "\" chưa chọn đủ lineup ("
                                    + spec.position() + ")");
                }
            }
            lineupByClub.put(p.getId(), byPos);
        }

        // Tạo / tìm category MEN_SINGLE (vẫn giữ category này làm root cho bracket club, dù chứa nhiều line type)
        BadmintonCategoryEnum singlesCategory = BadmintonCategoryEnum.MEN_SINGLE;
        TournamentCategory category = tournamentCategoryRepository
                .findByTournamentIdAndCategory(tournamentId, singlesCategory)
                .orElseGet(() -> tournamentCategoryRepository.save(TournamentCategory.builder()
                        .tournament(tournament)
                        .category(singlesCategory)
                        .build()));

        List<TournamentMatch> existingMatches = tournamentMatchRepository.findByCategory(category);
        if (!existingMatches.isEmpty()) {
            boolean anyStarted = existingMatches.stream()
                    .anyMatch(m -> m.getStatus() == MatchStatus.IN_PROGRESS
                            || m.getStatus() == MatchStatus.FINISHED);
            if (anyStarted) {
                throw new InvalidDataException("Không thể tạo lại bảng đấu khi đã có trận đang diễn ra hoặc kết thúc");
            }
            tournamentMatchRepository.deleteByCategory(category);
        }

        List<ClubTournamentParticipant> participantsList = new ArrayList<>(approved);
        int bracketSize = nextPowerOfTwo(participantsList.size());
        List<ClubTournamentParticipant> current = new ArrayList<>(participantsList);
        while (current.size() < bracketSize) current.add(null);
        int totalRounds = (int) (Math.log(bracketSize) / Math.log(2));

        for (int round = 1; round <= totalRounds; round++) {
            int tieIndex = 1;
            List<ClubTournamentParticipant> nextRound = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                ClubTournamentParticipant club1 = current.get(i);
                ClubTournamentParticipant club2 = current.get(i + 1);
                String tieId = java.util.UUID.randomUUID().toString();

                for (ClubLineupHelper.PositionSpec spec : positionSpecs) {
                    TournamentMatch m = TournamentMatch.builder()
                            .category(category)
                            .round(round)
                            .matchIndex(tieIndex)
                            .tieId(tieId)
                            .lineType(spec.lineType())
                            .lineIndex(spec.lineIndex())
                            .participant1Id(club1 != null ? club1.getId() : null)
                            .participant2Id(club2 != null ? club2.getId() : null)
                            .participant1Name(club1 != null
                                    ? buildRubberDisplayName(club1, spec, lineupByClub.get(club1.getId()))
                                    : null)
                            .participant2Name(club2 != null
                                    ? buildRubberDisplayName(club2, spec, lineupByClub.get(club2.getId()))
                                    : null)
                            .status(MatchStatus.NOT_STARTED)
                            .build();
                    // Mỗi rubber chỉ persist khi spec là "leader slot" (tránh save 2 lần cho doubles P1/P2)
                    // Doubles có 2 PositionSpec (P1, P2) nhưng chỉ cần 1 TournamentMatch row /rubber
                    if (spec.playerSlot() == null || spec.playerSlot() == 1) {
                        tournamentMatchRepository.save(m);
                    }
                }
                tieIndex++;
                nextRound.add(null);
            }
            current = nextRound;
        }

        return buildClubBracketResponse(category, tournament, totalRounds);
    }

    /**
     * Build display name cho 1 rubber dùng để hiển thị trên UI.
     * Singles: "{ClubName} - {Player full name}"
     * Doubles: "{ClubName} - {P1 name} / {P2 name}"
     */
    private String buildRubberDisplayName(
            ClubTournamentParticipant club,
            ClubLineupHelper.PositionSpec spec,
            Map<String, ClubTournamentRoster> lineup) {
        if (lineup == null) return club.getClub().getName();
        if (spec.lineType() == ClubLineTypeEnum.SINGLES) {
            ClubTournamentRoster r = lineup.get(spec.position());
            String name = r != null
                    ? r.getClubMember().getAccount().getUserInfo().getFullName()
                    : "?";
            return club.getClub().getName() + " - " + name;
        }
        // Doubles: lookup cả P1 và P2 (chỉ build 1 lần khi spec là P1)
        String p1Pos = ClubLineupHelper.formatPosition(spec.lineType(), spec.lineIndex(), 1);
        String p2Pos = ClubLineupHelper.formatPosition(spec.lineType(), spec.lineIndex(), 2);
        ClubTournamentRoster r1 = lineup.get(p1Pos);
        ClubTournamentRoster r2 = lineup.get(p2Pos);
        String n1 = r1 != null ? r1.getClubMember().getAccount().getUserInfo().getFullName() : "?";
        String n2 = r2 != null ? r2.getClubMember().getAccount().getUserInfo().getFullName() : "?";
        return club.getClub().getName() + " - " + n1 + " / " + n2;
    }

    // =========================================================
    // 10. LẤY BẢNG ĐẤU CLB (GET) - theo categoryId
    // =========================================================

    public ClubBracketResponse getClubBracket(String categoryId) {
        TournamentCategory category = tournamentCategoryRepository.findByIdWithTournament(categoryId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy hạng đấu"));
        return getClubBracketByCategory(category);
    }

    // =========================================================
    // 10b. LẤY BẢNG ĐẤU CLB - theo tournamentId
    // Tự động tìm category MEN_SINGLE hoặc trả về empty nếu chưa tạo
    // =========================================================

    public ClubBracketResponse getClubBracketByTournament(String tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy giải đấu"));

        if (tournament.getParticipationType() != TournamentParticipationTypeEnum.CLUB) {
            throw new InvalidDataException("Giải đấu không phải loại CLUB");
        }

        // Tìm category MEN_SINGLE
        TournamentCategory category = tournamentCategoryRepository
                .findByTournamentIdAndCategory(tournamentId, BadmintonCategoryEnum.MEN_SINGLE)
                .orElse(null);

        // Chưa tạo category → trả về empty response
        if (category == null) {
            return ClubBracketResponse.builder()
                    .tournamentId(tournament.getId())
                    .tournamentName(tournament.getName())
                    .categoryId(null)
                    .categoryName(BadmintonCategoryEnum.MEN_SINGLE.getLabel())
                    .totalRounds(0)
                    .rounds(List.of())
                    .build();
        }

        return getClubBracketByCategory(category);
    }

    private ClubBracketResponse getClubBracketByCategory(TournamentCategory category) {
        Tournament tournament = category.getTournament();
        List<TournamentMatch> matches = tournamentMatchRepository.findByCategory(category);
        int totalRounds = matches.stream()
                .mapToInt(TournamentMatch::getRound)
                .max()
                .orElse(0);
        return buildClubBracketResponse(category, tournament, totalRounds);
    }

    // =========================================================
    // HELPER
    // =========================================================

    private ClubBracketResponse buildClubBracketResponse(
            TournamentCategory category,
            Tournament tournament,
            int totalRounds
    ) {
        List<TournamentMatch> matches = tournamentMatchRepository.findByCategory(category);

        // Mọi đăng ký còn hiệu lực (không hủy / từ chối), gồm ELIMINATED — loser sau mỗi tie bị
        // ELIMINATED nhưng vẫn phải hiển thị tên trên bracket & rubber (không chỉ APPROVED).
        List<ClubTournamentParticipant> allInTournament =
                clubTournamentParticipantRepository.findByTournamentId(tournament.getId());
        Map<String, ClubTournamentParticipant> clubByParticipantId = new HashMap<>();
        Map<String, Map<String, ClubTournamentRoster>> rosterByClub = new HashMap<>();
        for (ClubTournamentParticipant p : allInTournament) {
            ClubTournamentParticipantStatusEnum st = p.getStatus();
            if (st == ClubTournamentParticipantStatusEnum.CANCELLED
                    || st == ClubTournamentParticipantStatusEnum.REJECTED) {
                continue;
            }
            clubByParticipantId.put(p.getId(), p);
            Map<String, ClubTournamentRoster> byPos = new HashMap<>();
            for (ClubTournamentRoster r : clubTournamentRosterRepository.findByClubTournamentParticipant(p)) {
                if (r.getPosition() != null) byPos.put(r.getPosition(), r);
            }
            rosterByClub.put(p.getId(), byPos);
        }

        List<ClubBracketRoundResponse> rounds = new ArrayList<>();
        for (int roundNum = 1; roundNum <= totalRounds; roundNum++) {
            final int r = roundNum;
            List<TournamentMatch> roundMatches = matches.stream()
                    .filter(m -> m.getRound() == r)
                    .sorted(java.util.Comparator
                            .comparing(TournamentMatch::getMatchIndex)
                            .thenComparing(m -> m.getLineType() == null ? "" : m.getLineType().name())
                            .thenComparing(m -> m.getLineIndex() == null ? 0 : m.getLineIndex()))
                    .toList();

            // Detect: matches có tieId không?
            boolean hasTies = roundMatches.stream().anyMatch(m -> m.getTieId() != null);
            if (hasTies) {
                List<ClubBracketTieResponse> ties = buildTiesForRound(roundMatches, clubByParticipantId, rosterByClub);
                rounds.add(ClubBracketRoundResponse.builder()
                        .round(roundNum)
                        .ties(ties)
                        .build());
            } else {
                // Legacy single-match path
                List<ClubBracketMatchResponse> matchResponses = roundMatches.stream()
                        .map(m -> toClubMatchResponseLegacy(m, clubByParticipantId, rosterByClub))
                        .toList();
                rounds.add(ClubBracketRoundResponse.builder()
                        .round(roundNum)
                        .matches(matchResponses)
                        .build());
            }
        }

        return ClubBracketResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .categoryId(category.getId())
                .categoryName(category.getCategory().getLabel())
                .totalRounds(totalRounds)
                .rounds(rounds)
                .build();
    }

    private List<ClubBracketTieResponse> buildTiesForRound(
            List<TournamentMatch> roundMatches,
            Map<String, ClubTournamentParticipant> clubByParticipantId,
            Map<String, Map<String, ClubTournamentRoster>> rosterByClub) {
        // Group by tieId
        Map<String, List<TournamentMatch>> grouped = new LinkedHashMap<>();
        for (TournamentMatch m : roundMatches) {
            grouped.computeIfAbsent(m.getTieId(), k -> new ArrayList<>()).add(m);
        }
        List<ClubBracketTieResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<TournamentMatch>> e : grouped.entrySet()) {
            List<TournamentMatch> rubbers = e.getValue();
            TournamentMatch first = rubbers.get(0);

            ClubTournamentParticipant club1 = first.getParticipant1Id() != null
                    ? clubByParticipantId.get(first.getParticipant1Id()) : null;
            ClubTournamentParticipant club2 = first.getParticipant2Id() != null
                    ? clubByParticipantId.get(first.getParticipant2Id()) : null;

            int club1Wins = 0, club2Wins = 0;
            int club1Sets = 0, club2Sets = 0;
            int finishedCount = 0;
            int notStartedCount = 0;
            for (TournamentMatch m : rubbers) {
                if (m.getStatus() == MatchStatus.FINISHED) {
                    finishedCount++;
                    if (m.getWinnerId() != null) {
                        if (m.getWinnerId().equals(first.getParticipant1Id())) club1Wins++;
                        else if (m.getWinnerId().equals(first.getParticipant2Id())) club2Wins++;
                    }
                    // Aggregate sets won
                    int p1s = 0, p2s = 0;
                    List<Integer> s1 = m.getSetScoreP1(), s2 = m.getSetScoreP2();
                    int len = Math.min(s1 == null ? 0 : s1.size(), s2 == null ? 0 : s2.size());
                    for (int i = 0; i < len; i++) {
                        if (s1.get(i) > s2.get(i)) p1s++;
                        else if (s2.get(i) > s1.get(i)) p2s++;
                    }
                    club1Sets += p1s;
                    club2Sets += p2s;
                }
                if (m.getStatus() == MatchStatus.NOT_STARTED || m.getStatus() == null) notStartedCount++;
            }
            String winnerClubId = null;
            if (club1Wins > club2Wins) winnerClubId = first.getParticipant1Id();
            else if (club2Wins > club1Wins) winnerClubId = first.getParticipant2Id();
            else if (club1Wins == club2Wins && finishedCount == rubbers.size()
                    && club1Wins > 0 && rubbers.size() > 0) {
                // Tie-breaker theo set won
                if (club1Sets > club2Sets) winnerClubId = first.getParticipant1Id();
                else if (club2Sets > club1Sets) winnerClubId = first.getParticipant2Id();
            }
            String tieStatus;
            if (notStartedCount == rubbers.size()) tieStatus = "NOT_STARTED";
            else if (winnerClubId != null) tieStatus = "FINISHED";
            else tieStatus = "IN_PROGRESS";

            ClubBracketTieResponse tieResp = ClubBracketTieResponse.builder()
                    .tieId(e.getKey())
                    .round(first.getRound())
                    .matchIndex(first.getMatchIndex())
                    .club1(club1 != null ? buildClubInfo(club1) : null)
                    .club2(club2 != null ? buildClubInfo(club2) : null)
                    .club1RubberWins(club1Wins)
                    .club2RubberWins(club2Wins)
                    .club1SetsWon(club1Sets)
                    .club2SetsWon(club2Sets)
                    .winnerClubParticipantId(winnerClubId)
                    .status(tieStatus)
                    .rubbers(rubbers.stream()
                            .map(m -> toRubberResponse(m, rosterByClub))
                            .toList())
                    .build();
            result.add(tieResp);
        }
        return result;
    }

    private ClubBracketRubberResponse toRubberResponse(
            TournamentMatch m,
            Map<String, Map<String, ClubTournamentRoster>> rosterByClub) {
        ClubLineTypeEnum type = m.getLineType();
        Integer idx = m.getLineIndex();
        String label = (type != null ? type.getLabel() : "Ván") + (idx != null ? " #" + idx : "");

        return ClubBracketRubberResponse.builder()
                .matchId(m.getId())
                .lineType(type)
                .lineIndex(idx)
                .label(label)
                .club1Players(buildRubberPlayers(m.getParticipant1Id(), type, idx, rosterByClub))
                .club2Players(buildRubberPlayers(m.getParticipant2Id(), type, idx, rosterByClub))
                .setScoreP1(m.getSetScoreP1())
                .setScoreP2(m.getSetScoreP2())
                .winnerClubParticipantId(m.getWinnerId())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .build();
    }

    private List<ClubMatchParticipantResponse> buildRubberPlayers(
            String clubParticipantId,
            ClubLineTypeEnum type,
            Integer lineIndex,
            Map<String, Map<String, ClubTournamentRoster>> rosterByClub) {
        if (clubParticipantId == null || type == null || lineIndex == null) return List.of();
        Map<String, ClubTournamentRoster> roster = rosterByClub.get(clubParticipantId);
        if (roster == null) return List.of();
        List<ClubMatchParticipantResponse> result = new ArrayList<>();
        if (type == ClubLineTypeEnum.SINGLES) {
            ClubTournamentRoster r = roster.get(ClubLineupHelper.formatPosition(type, lineIndex, null));
            if (r != null) result.add(rosterToParticipant(clubParticipantId, r));
        } else {
            for (int slot = 1; slot <= 2; slot++) {
                ClubTournamentRoster r = roster.get(ClubLineupHelper.formatPosition(type, lineIndex, slot));
                if (r != null) result.add(rosterToParticipant(clubParticipantId, r));
            }
        }
        return result;
    }

    private ClubMatchParticipantResponse rosterToParticipant(
            String clubParticipantId,
            ClubTournamentRoster r) {
        Account acc = r.getClubMember().getAccount();
        ClubTournamentParticipant ctp = r.getClubTournamentParticipant();
        Club club = ctp.getClub();
        return ClubMatchParticipantResponse.builder()
                .participantId(clubParticipantId)
                .clubId(club.getId())
                .clubName(club.getName())
                .clubLogoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .memberId(acc.getId())
                .memberName(acc.getUserInfo().getFullName())
                .memberAvatarUrl(fileStorageService.getFileUrl(acc.getUserInfo().getAvatarUrl(), "/avatar"))
                .build();
    }

    private ClubMatchParticipantResponse buildClubInfo(ClubTournamentParticipant p) {
        return ClubMatchParticipantResponse.builder()
                .participantId(p.getId())
                .clubId(p.getClub().getId())
                .clubName(p.getClub().getName())
                .clubLogoUrl(fileStorageService.getFileUrl(p.getClub().getLogoUrl(), "/club/logo"))
                .build();
    }

    /** Legacy mapping cho tournament cũ (1 match/cặp, không có tieId). */
    private ClubBracketMatchResponse toClubMatchResponseLegacy(
            TournamentMatch m,
            Map<String, ClubTournamentParticipant> clubByParticipantId,
            Map<String, Map<String, ClubTournamentRoster>> rosterByClub) {
        ClubMatchParticipantResponse p1 = resolveLegacyParticipant(m.getParticipant1Id(), clubByParticipantId, rosterByClub);
        ClubMatchParticipantResponse p2 = resolveLegacyParticipant(m.getParticipant2Id(), clubByParticipantId, rosterByClub);
        return ClubBracketMatchResponse.builder()
                .matchId(m.getId())
                .round(m.getRound())
                .matchIndex(m.getMatchIndex())
                .player1(p1)
                .player2(p2)
                .setScoreP1(m.getSetScoreP1())
                .setScoreP2(m.getSetScoreP2())
                .winnerId(m.getWinnerId())
                .winnerName(m.getWinnerName())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .build();
    }

    private ClubMatchParticipantResponse resolveLegacyParticipant(
            String clubParticipantId,
            Map<String, ClubTournamentParticipant> clubByParticipantId,
            Map<String, Map<String, ClubTournamentRoster>> rosterByClub) {
        if (clubParticipantId == null) return null;
        ClubTournamentParticipant p = clubByParticipantId.get(clubParticipantId);
        if (p == null) return null;
        Map<String, ClubTournamentRoster> roster = rosterByClub.get(clubParticipantId);
        ClubTournamentRoster rep = null;
        if (roster != null) {
            rep = roster.get("SINGLES_1");
            if (rep == null) rep = roster.get("SINGLES");
        }
        Account acc = rep != null ? rep.getClubMember().getAccount() : null;
        return ClubMatchParticipantResponse.builder()
                .participantId(p.getId())
                .clubId(p.getClub().getId())
                .clubName(p.getClub().getName())
                .clubLogoUrl(fileStorageService.getFileUrl(p.getClub().getLogoUrl(), "/club/logo"))
                .memberId(acc != null ? acc.getId() : null)
                .memberName(acc != null ? acc.getUserInfo().getFullName() : null)
                .memberAvatarUrl(acc != null
                        ? fileStorageService.getFileUrl(acc.getUserInfo().getAvatarUrl(), "/avatar")
                        : null)
                .build();
    }

    private int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p *= 2;
        return p;
    }

}
