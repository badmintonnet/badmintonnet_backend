package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.club.ClubAdminResponse;
import com.tlcn.sportsnet_backend.dto.club.ClubCreateRequest;
import com.tlcn.sportsnet_backend.dto.club.ClubResponse;
import com.tlcn.sportsnet_backend.dto.club.MyClubResponse;
import com.tlcn.sportsnet_backend.dto.clubWarning.ClubWarningResponse;
import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.*;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.*;
import com.tlcn.sportsnet_backend.util.ClubSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;


import java.security.Permission;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubService {
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final ClubEventRatingRepository clubEventRatingRepository;
    private final FileStorageService fileStorageService;
    private final ClubEventRepository clubEventRepository;
    private final ClubEventParticipantRepository clubEventParticipantRepository;
    private final ConversationService conversationService;
    private final FacilityRepository facilityRepository;
    private final ClubInvitationRepository clubInvitationRepository;
    private final ClubWarningRepository clubWarningRepository;

    public ClubResponse createClub(ClubCreateRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Account owner = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Owner not found"));

        Role ownerRole = roleRepository.findByName("ROLE_CLUB_OWNER")
                .orElseThrow(() -> new InvalidDataException("Role OWNER_CLUB not found"));

        Set<Role> roles = owner.getRoles();
        roles.add(ownerRole);
        owner.setRoles(roles);
        roleRepository.save(ownerRole);

        Facility facility = null;
        if (request.getFacilityId() != null && !request.getFacilityId().isBlank()) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new InvalidDataException("Facility not found"));
        }

        if (facility == null && (request.getLocation() == null || request.getLocation().isBlank())) {
            throw new InvalidDataException("Cần chọn cơ sở hoặc nhập địa điểm tùy chỉnh");
        }

        Club club = Club.builder()
                .name(request.getName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .location(request.getLocation())
                .facility(facility)
                .maxMembers(request.getMaxMembers())
                .minLevel(request.getMinLevel())
                .maxLevel(request.getMaxLevel())
                .visibility(request.getVisibility())
                .tags(request.getTags() != null ? request.getTags() : new HashSet<>())
                .owner(owner)
                .build();

        club = clubRepository.save(club);
        ClubMember clubMember = ClubMember.builder()
                .club(club)
                .account(owner)
                .role(ClubMemberRoleEnum.OWNER)
                .status(ClubMemberStatusEnum.APPROVED)
                .joinedAt(Instant.now())
                .build();

        clubMemberRepository.save(clubMember);
        conversationService.createConversationByClub(club);
        return toClubResponse(club, owner, false);
    }

    public ClubResponse updateClub(String clubId, ClubCreateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Account currentUser = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy tài khoản người dùng"));

        // Tìm club
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new InvalidDataException("Không tìm thấy câu lạc bộ"));

        // Chỉ chủ sở hữu mới được chỉnh sửa
        if (!club.getOwner().getId().equals(currentUser.getId())) {
            throw new InvalidDataException("Bạn không có quyền chỉnh sửa câu lạc bộ này");
        }

        // Kiểm tra facility (nếu có)
        Facility facility = null;
        if (request.getFacilityId() != null && !request.getFacilityId().isBlank()) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new InvalidDataException("Không tìm thấy cơ sở"));
        }

        if (facility == null && (request.getLocation() == null || request.getLocation().isBlank())) {
            throw new InvalidDataException("Cần chọn cơ sở hoặc nhập địa điểm tùy chỉnh");
        }

        // Cập nhật thông tin club
        club.setName(request.getName());
        club.setDescription(request.getDescription());
        if(request.getLogoUrl() != null && !request.getLogoUrl().isBlank()) {
            club.setLogoUrl(request.getLogoUrl());
        }
        club.setLocation(request.getLocation());
        club.setFacility(facility);
        club.setMaxMembers(request.getMaxMembers());
        club.setMinLevel(request.getMinLevel());
        club.setMaxLevel(request.getMaxLevel());
        club.setVisibility(request.getVisibility());
        club.setTags(request.getTags() != null ? request.getTags() : new HashSet<>());

        club = clubRepository.save(club);

        return toClubResponse(club, currentUser, false);
    }

    public PagedResponse<ClubResponse> getAllClubPublic(
            int page,
            int size,
            String search,
            String province,
            String ward,
            List<String> selectedLevels,
            String reputationSort,
            List<String> facilityNames
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElse(null);

        Sort sort = Sort.by(
                reputationSort != null && reputationSort.equalsIgnoreCase("asc")
                        ? Sort.Order.asc("reputation")
                        : Sort.Order.desc("reputation"),
                Sort.Order.desc("createdAt")
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Club> spec = Specification.allOf(
                ClubSpecification.hasVisibilityAndStatus(ClubVisibilityEnum.PUBLIC, ClubStatusEnum.ACTIVE),
                ClubSpecification.matchesSearch(search),
                ClubSpecification.matchesProvince(province),
                ClubSpecification.matchesWard(ward),
                ClubSpecification.matchesLevels(selectedLevels),
                ClubSpecification.matchesFacilityNames(facilityNames)
        );

        if (account != null) {
            spec = spec.and(ClubSpecification.notJoinedBy(account));
        }

        Page<Club> clubs = clubRepository.findAll(spec, pageable);


        List<ClubResponse> content = clubs.stream()
                .map(club -> toClubResponse(club,account, true))
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

    public PagedResponse<MyClubResponse> getAllMyClub(int page, int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Club> clubs = clubRepository.findAvailableClubsBelongUserAndStatus( account, ClubStatusEnum.ACTIVE, pageable);
        List<String> clubIds = clubs.getContent().stream().map(Club::getId).toList();

        List<Object[]> counts = clubMemberRepository.countMembersByClubIds(
                clubIds, ClubMemberStatusEnum.APPROVED
        );

        Map<String, Long> memberCountMap = counts.stream()
                .collect(Collectors.toMap(
                        obj -> (String) obj[0],
                        obj -> (Long) obj[1]
                ));
        List<MyClubResponse> content = new ArrayList<>();
        for (Club club : clubs) {
            content.add(toMyClubResponseList(club, account, memberCountMap));
        }
// Sắp xếp: owner lên đầu
        content.sort((c1, c2) -> Boolean.compare(!c1.isOwner(), !c2.isOwner()));
        return new PagedResponse<>(
                content,
                clubs.getNumber(),
                clubs.getSize(),
                clubs.getTotalElements(),
                clubs.getTotalPages(),
                clubs.isLast()
        );
    }
    public MyClubResponse getMyClubInformation(String slug) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        System.out.println(account.getEmail());
        Club club = clubRepository.findBySlug(slug).orElseThrow(() -> new InvalidDataException("Club not found"));
        System.out.println(club.getName());
        ClubMember clubMember = clubMemberRepository.findClubMemberByAccountAndClub(account, club);
//        System.out.println(clubMember.toString());
        if(clubMember.getStatus() != ClubMemberStatusEnum.APPROVED) {
            throw new InvalidDataException("ClubMember is not approved");
        }
        return toMyClubResponse(club, account, false);
    }

    public ClubResponse getClubInformation(String slug) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Club club = clubRepository.findBySlug(slug).orElseThrow(() -> new InvalidDataException("Club not found"));
        return toClubResponse(club, account, false);
    }

    public List<ClubResponse> getNearestClubs(int top) {
        Coordinate coordinate = resolveCurrentUserCoordinate();
        validateCoordinate(coordinate.latitude(), coordinate.longitude());
        int limit = normalizeTop(top);

        Pageable pageable = PageRequest.of(0, limit);
        List<Club> clubs = clubRepository.findNearestClubs(
                ClubVisibilityEnum.PUBLIC,
                ClubStatusEnum.ACTIVE,
                coordinate.latitude(),
                coordinate.longitude(),
                pageable
        );

        return clubs.stream()
                .map(club -> toClubResponse(club, null, true))
                .toList();
    }

    private ClubResponse toClubResponse(Club club, Account account, boolean getList) {
        boolean joined = false;
        boolean owner = false;
        String invitationId = null;
        String invitationMessage = null;
        if(!getList) {
            if (account != null) {
                ClubMember clubMember = clubMemberRepository.findByClubAndAccount(club, account);
                if (clubMember != null) {
                    joined = clubMember.getStatus() == ClubMemberStatusEnum.APPROVED;
                }
                ClubInvitation clubInvitation = clubInvitationRepository.findByReceiver_IdAndClub_IdAndStatus(account.getId(), club.getId(), InvitationStatusEnum.PENDING).orElse(null);
                if (clubInvitation != null) {
                    invitationId = clubInvitation.getId();
                    invitationMessage = clubInvitation.getMessage();
                }
                owner = club.getOwner().getId().equals(account.getId());
            }
        }

//        List<ClubMember> members = clubMemberRepository.findByClubIdAndStatus(club.getId(), ClubMemberStatusEnum.APPROVED);
        long count = 0;
//        if(!getList) {
            count = clubMemberRepository.countByClubIdAndStatus(club.getId(), ClubMemberStatusEnum.APPROVED);
//        }
        long totalEvent = clubEventRepository.countByClubIdAndStatus(club.getId(), EventStatusEnum.FINISHED);
        return ClubResponse.builder()
                .id(club.getId())
                .slug(club.getSlug())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .location(club.getLocation())
                .facility(club.getFacility() != null ? toFacilityResponse(club.getFacility()) : null)
                .memberCount((int) count)
                .maxMembers(club.getMaxMembers())
                .minLevel(club.getMinLevel())
                .maxLevel(club.getMaxLevel())
                .visibility(club.getVisibility())
                .tags(club.getTags())
                .status(club.getStatus())
                .ownerName(getList ? null : club.getOwner().getUserInfo().getFullName())
                .owner(owner)
                .joined(joined)
                .createdAt(club.getCreatedAt())
                .invitationId(invitationId)
                .invitationMessage(invitationMessage)
                .totalEvent((int) totalEvent)
                .build();
    }

    private MyClubResponse toMyClubResponse(Club club, Account account, boolean getList) {

        ClubMember member = clubMemberRepository.findByClubAndAccount(club, account);
        Instant joinAt = club.getCreatedAt();
        if (member != null) {
            joinAt = member.getJoinedAt();
        }
        long count = clubMemberRepository.countByClubIdAndStatus(club.getId(), ClubMemberStatusEnum.APPROVED);
        List<ClubWarningResponse> clubWarningResponses = new ArrayList<>();
        if(!getList) {
            List<ClubWarning> clubWarnings = clubWarningRepository.findAllByClubMemberOrderByCreatedAtDesc(member);
            for(ClubWarning warning : clubWarnings) {
                clubWarningResponses.add(ClubWarningResponse.builder()
                                .reason(warning.getReason())
                                .updatedAt(warning.getUpdatedAt())
                                .createdAt(warning.getCreatedAt())
                                .status(warning.getStatus())
                                .id(warning.getId())
                        .build());
            }
        }
        long totalEvent = clubEventRepository.countByClubIdAndStatus(club.getId(), EventStatusEnum.FINISHED);
        assert member != null;
        return MyClubResponse.builder()
                .id(club.getId())
                .slug(club.getSlug())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .location(club.getLocation())
                .facility(club.getFacility() != null ? toFacilityResponse(club.getFacility()) : null)
                .maxMembers(club.getMaxMembers())
                .visibility(club.getVisibility())
                .tags(club.getTags())
                .status(club.getStatus())
                .memberStatus(member.getStatus())
                .ownerName(club.getOwner().getUserInfo().getFullName())
                .createdAt(club.getCreatedAt())
                .dateJoined(joinAt)
                .memberCount((int) count)
                .minLevel(club.getMinLevel())
                .maxLevel(club.getMaxLevel())
                .isOwner(club.getOwner()==account)
                .clubWarnings(clubWarningResponses)
                .totalEvent((int) totalEvent)
                .build();
    }
    private MyClubResponse toMyClubResponseList(Club club, Account account,Map<String, Long> memberCountMap) {

        ClubMember member = clubMemberRepository.findByClubAndAccount(club, account);
        Instant joinAt = club.getCreatedAt();
        if (member != null) {
            joinAt = member.getJoinedAt();
        }
        long count = memberCountMap.getOrDefault(club.getId(), 0L);
        List<ClubWarningResponse> clubWarningResponses = new ArrayList<>();
        return MyClubResponse.builder()
                .id(club.getId())
                .slug(club.getSlug())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo"))
                .location(club.getLocation())
                .facility(club.getFacility() != null ? toFacilityResponse(club.getFacility()) : null)
                .maxMembers(club.getMaxMembers())
                .visibility(club.getVisibility())
                .tags(club.getTags())
                .status(club.getStatus())
                .memberStatus(member.getStatus())
                .ownerName(club.getOwner().getUserInfo().getFullName())
                .createdAt(club.getCreatedAt())
                .dateJoined(joinAt)
                .memberCount((int) count)
                .minLevel(club.getMinLevel())
                .maxLevel(club.getMaxLevel())
                .isOwner(club.getOwner().getId().equals(account.getId()))
                .clubWarnings(clubWarningResponses)
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


    public double calculateReputation(Club club) {
        // ----- 1. Weighted Event Rating -----
        double weightedEventRating = clubEventRatingRepository.getWeightedAverageRatingByClubId(club.getId());
        if (Double.isNaN(weightedEventRating)) weightedEventRating = 0.0;
        // ----- 2. Activity Score -----
        long countEvent = clubEventRepository.countByClubIdAndStatus(club.getId(), EventStatusEnum.FINISHED);
        Long maxCountEventLong = clubEventRepository.findMaxEventCount(EventStatusEnum.FINISHED);
        long maxCountEvent = (maxCountEventLong != null) ? maxCountEventLong : 0L;

        double activityScore = 0.0;
        if (maxCountEvent > 0) {
            activityScore = Math.log(1 + countEvent) / Math.log(1 + maxCountEvent) * 100;
            if (Double.isNaN(activityScore) || Double.isInfinite(activityScore)) activityScore = 0.0;
        }
        // ----- 3. Engagement Score -----
        long totalApprovedParticipants = clubEventParticipantRepository.countByClubIdAndStatus(club.getId(), ClubEventParticipantStatusEnum.APPROVED);
        Long totalEventCapacityLong = clubEventRepository.sumTotalMemberByClubId(club.getId());
        long totalEventCapacity = (totalEventCapacityLong != null) ? totalEventCapacityLong : 0L;

        double engagementScore = 0.0;
        if (totalEventCapacity > 0) {
            engagementScore = (double) totalApprovedParticipants / totalEventCapacity * 100;
            if (Double.isNaN(engagementScore) || Double.isInfinite(engagementScore)) engagementScore = 0.0;
        }


        // ----- 4. Longevity Score -----
        int currentYear = ZonedDateTime.now(ZoneId.systemDefault()).getYear();
        int createdYear = ZonedDateTime.ofInstant(club.getCreatedAt(), ZoneId.systemDefault()).getYear();
        int yearsSinceFounded = Math.max(currentYear - createdYear, 0); // tránh âm
        int longevityScore = Math.min(yearsSinceFounded * 10, 20);


        // ----- 5. Tính tổng uy tín -----
        double clubReputation = 0.4 * (weightedEventRating / 5) * 100
                + 0.2 * activityScore
                + 0.2 * engagementScore
                + 0.2 * longevityScore;


        // ----- 6. Lưu vào DB -----
        club.setReputation(clubReputation);
        clubRepository.save(club);

        return clubReputation;
    }



    public void calculateAllClubReputationOnStartup() {
        List<Club> clubs = clubRepository.findAll();
        for (Club club : clubs) {
            double reputation = calculateReputation(club);
            System.out.println("CLB: " + club.getName() + " - Reputation: " + reputation);
            // Bạn có thể lưu vào DB nếu cần
        }
    }

    private void validateCoordinate(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new InvalidDataException("Latitude phai trong khoang -90 den 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new InvalidDataException("Longitude phai trong khoang -180 den 180");
        }
    }

    private int normalizeTop(int top) {
        if (top <= 0) {
            return 5;
        }
        return Math.min(top, 5);
    }

    private Coordinate resolveCurrentUserCoordinate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        UserInfo userInfo = account.getUserInfo();
        if (userInfo == null || userInfo.getLatitude() == null || userInfo.getLongitude() == null) {
            throw new InvalidDataException("Vui long cap nhat dia chi de he thong xac dinh vi tri cua ban");
        }
        return new Coordinate(userInfo.getLatitude(), userInfo.getLongitude());
    }

    private record Coordinate(double latitude, double longitude) {}
}
