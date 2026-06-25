package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.account.AccountAdminResponse;
import com.tlcn.sportsnet_backend.dto.account.AccountResponse;
import com.tlcn.sportsnet_backend.dto.club.ClubAdminResponse;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventAdminResponse;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventResponse;
import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.dto.tournament.TournamentAdminResponse;
import com.tlcn.sportsnet_backend.dto.tournament.TournamentCategoryResponse;
import com.tlcn.sportsnet_backend.dto.tournament.TournamentResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.ClubEventParticipantStatusEnum;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.enums.ClubStatusEnum;
import com.tlcn.sportsnet_backend.enums.ParticipantRoleEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final RoleRepository roleRepository;
    private final ClubEventRepository clubEventRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final ClubEventService clubEventService;
    private final FileStorageService fileStorageService;
    private final TournamentRepository tournamentRepository;
    private final NotificationService notificationService;
    private final AdminNotificationService adminNotificationService;

    public PagedResponse<ClubAdminResponse> getAllClubs(int page, int size) {
        checkAccount();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt") ));
        Page<Club> clubs = clubRepository.findAll(pageable);

        List<ClubAdminResponse> content = clubs.stream()
                .map(this::toClubAdminResponse)
                .toList();

        return new PagedResponse<>(
                content,
                clubs.getNumber(),
                clubs.getSize(),
                clubs.getTotalElements(),
                clubs.getTotalPages(),
                clubs.isLast()
        );
    }

    public void updateClubStatus(String id, ClubStatusEnum newStatus) {
         checkAccount();
        Club club = clubRepository.findBySlug(id).orElseThrow(() -> new InvalidDataException("Club not found"));

        club.setStatus(newStatus);

        if(newStatus == ClubStatusEnum.ACTIVE) {
            Account account = club.getOwner();
            Role ownerRole = roleRepository.findByName("ROLE_CLUB_OWNER")
                    .orElseThrow(() -> new InvalidDataException("Role OWNER_CLUB not found"));

            account.getRoles().add(ownerRole);
            clubRepository.save(club);
            accountRepository.save(account);
        }

        clubRepository.save(club);

        notificationService.sendToAccount(
                club.getOwner().getEmail(),
                "Trạng thái CLB thay đổi",
                "CLB \"" + club.getName() + "\" của bạn đã được cập nhật sang trạng thái: " + newStatus.name(),
                "/clubs/" + club.getSlug()
        );
        adminNotificationService.notifyAllAdmins(
                "Trạng thái CLB thay đổi",
                "CLB \"" + club.getName() + "\" đã được cập nhật sang: " + newStatus.name(),
                "/admin/clubs"
        );
    }
    public void deleteClub(String id) {
        checkAccount();
        Club club = clubRepository.findBySlug(id).orElseThrow(() -> new InvalidDataException("Club not found"));
        clubRepository.delete(club);
    }


    private ClubAdminResponse toClubAdminResponse(Club club) {
        long count = clubMemberRepository.countByClubIdAndStatus(club.getId(), ClubMemberStatusEnum.APPROVED);
        return ClubAdminResponse.builder()
                .id(club.getId())
                .slug(club.getSlug())
                .email(club.getCreatedBy())
                .name(club.getName())
                .maxMembers(club.getMaxMembers())
                .status(club.getStatus())
                .ownerName(club.getOwner().getUserInfo().getFullName())
                .createdAt(club.getCreatedAt())
                .memberCount((int) count)
                .reputation(club.getReputation())
                .build();
    }

    public PagedResponse<ClubEventAdminResponse> getAllAdminEvent(int page, int size) {
        checkAccount();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt") ));
        Page<ClubEvent> clubEvents = clubEventRepository.findAll(pageable);
        List<ClubEventAdminResponse> content = clubEvents.stream()
                .map(this::toClubEventAdminResponse)
                .toList();
        return new PagedResponse<>(
                content,
                clubEvents.getNumber(),
                clubEvents.getSize(),
                clubEvents.getTotalElements(),
                clubEvents.getTotalPages(),
                clubEvents.isLast()
        );
    }

    private ClubEventAdminResponse toClubEventAdminResponse(ClubEvent event) {
        return ClubEventAdminResponse.builder()
                .id(event.getId())
                .slug(event.getSlug())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .location(event.getLocation())
                .facility(event.getFacility() != null ? toFacilityResponse(event.getFacility()) : null)
                .title(event.getTitle())
                .fee(event.getFee())
                .joinedMember((int) event.getParticipants().stream()
                        .filter(p -> !p.getStatus().equals(ClubEventParticipantStatusEnum.PENDING) )
                        .count())
                .totalMember(event.getTotalMember())
                .nameClub(event.getClub().getName())
                .openForOutside(event.isOpenForOutside())
                .status(event.getStatus())
                .maxLevel(event.getMaxLevel())
                .minLevel(event.getMinLevel())
                .build();

    }

    private FacilityResponse toFacilityResponse(Facility facility) {
        return FacilityResponse.builder()
                .id(facility.getId())
                .name(facility.getName())
                .address(facility.getAddress())
                .district(facility.getDistrict())
                .city(facility.getCity())
                .location(facility.getLocation())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .image(fileStorageService.getFileUrl(facility.getImage(), "/facility"))
                .build();
    }

    public void checkAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Set<Role> roles = account.getRoles();
        boolean isAdmin = roles.stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        if(!isAdmin)
        {
            throw new InvalidDataException("You are not an admin");
        }
    }

    public PagedResponse<AccountAdminResponse> getAllAccounts(int page, int size) {
        checkAccount();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt") ));
        Page<Account> accounts = accountRepository.findAll(pageable);
        List<AccountAdminResponse> content = accounts.stream()
                .map(this::toAccountAdminResponse)
                .toList();
        return new PagedResponse<>(
                content,
                accounts.getNumber(),
                accounts.getSize(),
                accounts.getTotalElements(),
                accounts.getTotalPages(),
                accounts.isLast()
        );
    }

    private AccountAdminResponse toAccountAdminResponse(Account account) {
        String roleName = "Người dùng";
        Set<Role> roles = account.getRoles();
        if(roles.stream()
                .anyMatch(role -> role.getName().equals("ROLE_CLUB_OWNER"))){
            roleName = "Chủ CLB";
        }
        else if(roles.stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"))){
            roleName = "Admin";
        }
        List<AccountAdminResponse.OwnerClub> ownerClubs = new ArrayList<>();
        if(roleName.equals("Chủ CLB")){
            List<Club> clubs = clubRepository.findAllByOwnerAndStatusOrderByReputationDesc(account, ClubStatusEnum.ACTIVE);
            for(Club club : clubs){
                ownerClubs.add(new AccountAdminResponse.OwnerClub(club.getName(), club.getSlug(), fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo") ));
            }
        }
        PlayerRating playerRating = playerRatingRepository.findByAccount(account).orElse(null);
        return AccountAdminResponse.builder()
                .id(account.getId())
                .phone(account.getUserInfo().getPhone())
                .enabled(account.isEnabled())
                .reputationScore(account.getReputationScore())
                .gender(account.getUserInfo().getGender())
                .address(account.getUserInfo().getAddress())
                .overallScore(playerRating != null ? playerRating.getOverallScore() : 0)
                .totalParticipatedEvents(account.getTotalParticipatedEvents())
                .role(roleName)
                .birthDate(account.getUserInfo().getBirthDate())
                .email(account.getEmail())
                .fullName(account.getUserInfo().getFullName())
                .ownerClubs(ownerClubs)
                .createdAt(account.getCreatedAt())
                .slug(account.getUserInfo().getSlug())
                
                .build();

    }

    public void updateAccountStatus(String id) {
        checkAccount();
        Account account = accountRepository.findById(id).orElseThrow(() -> new InvalidDataException("Account not found") );
        account.setEnabled(!account.isEnabled());
        account = accountRepository.save(account);

        String trangThai = account.isEnabled() ? "kích hoạt" : "tạm khóa";
        notificationService.sendToAccount(
                account.getEmail(),
                "Thông báo về tài khoản của bạn",
                "Tài khoản của bạn đã được " + trangThai + " bởi quản trị viên.",
                "/profile"
        );
        adminNotificationService.notifyAllAdmins(
                "Tài khoản bị " + trangThai,
                "Tài khoản " + account.getEmail() + " đã được " + trangThai,
                "/admin/users"
        );
    }

    public PagedResponse<TournamentAdminResponse> getAllAdminTournaments(int page, int size) {
        checkAccount();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc(     "startDate") ));
        Page<Tournament> tournaments = tournamentRepository.findAll(pageable);
        List<TournamentAdminResponse> content = new ArrayList<>();
        for (Tournament tournament : tournaments) {
            content.add(toTournamentAdminResponse(tournament));
        }
        return new PagedResponse<>(
                content,
                tournaments.getNumber(),
                tournaments.getSize(),
                tournaments.getTotalElements(),
                tournaments.getTotalPages(),
                tournaments.isLast()
        );

    }

    public TournamentAdminResponse toTournamentAdminResponse(Tournament tournament) {
        List<TournamentCategory> tournamentCategories = tournament.getCategories();
        List<TournamentCategoryResponse> tournamentCategoryResponses = new ArrayList<>();
        for (TournamentCategory tournamentCategory : tournamentCategories) {
            TournamentCategoryResponse tournamentCategoryResponse = TournamentCategoryResponse.builder()
                    .category(tournamentCategory.getCategory())
                    .id(tournamentCategory.getId())
                    .currentParticipantCount(0)
                    .maxParticipants(tournamentCategory.getMaxParticipants())
                    .build();
            tournamentCategoryResponses.add(tournamentCategoryResponse);
        }
        return TournamentAdminResponse.builder()
                .id(tournament.getId())
                .categories(tournamentCategoryResponses)
                .endDate(tournament.getEndDate())
                .startDate(tournament.getStartDate())
                .registrationStartDate(tournament.getRegistrationStartDate())
                .registrationEndDate(tournament.getRegistrationEndDate())
                .createdAt(tournament.getCreatedAt())
                .status(tournament.getStatus())
                .location(tournament.getLocation())
                .facility(tournament.getFacility() != null ? toFacilityResponse(tournament.getFacility()) : null)
                .slug(tournament.getSlug())
                .name(tournament.getName())
                .build();
    }
}
