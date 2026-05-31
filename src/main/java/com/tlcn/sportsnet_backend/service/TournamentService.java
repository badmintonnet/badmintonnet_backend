package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.dto.tournament.*;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import com.tlcn.sportsnet_backend.enums.TournamentParticipantEnum;
import com.tlcn.sportsnet_backend.enums.TournamentParticipationTypeEnum;
import com.tlcn.sportsnet_backend.enums.TournamentStatus;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final AccountRepository accountRepository;
    private final FileStorageService fileStorageService;
    private final FacilityRepository facilityRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final FriendshipService friendshipService;

    public TournamentResponse createTournament(TournamentCreateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new InvalidDataException("Account not found"));
        Set<Role> roles = account.getRoles();
        if(roles.stream()
                .noneMatch(role -> role.getName().equals("ROLE_ADMIN"))) {
            throw new InvalidDataException("You are not an admin");
        }

        Facility facility = null;
        if (request.getFacilityId() != null && !request.getFacilityId().isBlank()) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new InvalidDataException("Facility not found"));
        }

        if (facility == null && (request.getLocation() == null || request.getLocation().isBlank())) {
            throw new InvalidDataException("Cần chọn cơ sở hoặc nhập địa điểm tùy chỉnh");
        }

        TournamentParticipationTypeEnum participationType =
                request.getParticipationType() != null
                        ? request.getParticipationType()
                        : TournamentParticipationTypeEnum.INDIVIDUAL;

        Tournament tournament = Tournament.builder()
                .name(request.getName())
                .description(request.getDescription())
                .bannerUrl(request.getBannerUrl())
                .logoUrl(request.getLogoUrl())
                .location(request.getLocation())
                .facility(facility)
                .fee(request.getFee())
                .registrationEndDate(request.getRegistrationEndDate())
                .registrationStartDate(request.getRegistrationStartDate())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(TournamentStatus.UPCOMING)
                .rules(request.getRules())
                .participationType(participationType)
                .teamMatchFormat(request.getTeamMatchFormat())
                .clubRegistrationFee(request.getClubRegistrationFee())
                .minClubRosterSize(request.getMinClubRosterSize())
                .maxClubRosterSize(request.getMaxClubRosterSize())
                .maxClubs(request.getMaxClubs())
                .build();
        tournament = tournamentRepository.save(tournament);

        List<TournamentCategory> tournamentCategories = new ArrayList<>();

        if (request.getCategories() != null) {
            for (TournamentCategoryRequest c : request.getCategories()) {
                TournamentCategory category = TournamentCategory.builder()
                        .category(c.getCategoryType())
                        .tournament(tournament)
                        .minLevel(c.getMinLevel())
                        .maxLevel(c.getMaxLevel())
                        .maxParticipants(c.getMaxParticipants())
                        .registrationFee(c.getRegistrationFee())
                        .description(c.getDescription())
                        .rules(c.getRules())
                        .firstPrize(c.getFirstPrize())
                        .secondPrize(c.getSecondPrize())
                        .thirdPrize(c.getThirdPrize())
                        .build();

                tournamentCategories.add(category);
            }
            tournamentCategoryRepository.saveAll(tournamentCategories);
        }

        tournament.setCategories(tournamentCategories);
        return toTournamentResponse(tournament);
    }
    public PagedResponse<TournamentResponse> getAllTournament(
            int page,
            int size,
            TournamentParticipationTypeEnum participationType,
            String content,
            LocalDate organizationDateFrom,
            LocalDate organizationDateTo
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startDate").descending());
        LocalDateTime startDateFrom = organizationDateFrom != null ? organizationDateFrom.atStartOfDay() : null;
        LocalDateTime startDateTo = organizationDateTo != null ? organizationDateTo.atTime(23, 59, 59) : null;
        String normalizedContent = (content != null && !content.isBlank()) ? content.trim() : null;
        Page<Tournament> tournamentPage = tournamentRepository.searchTournaments(
                pageable,
                TournamentStatus.CANCELLED,
                participationType,
                normalizedContent,
                startDateFrom,
                startDateTo
        );
        List<TournamentResponse> responses = new ArrayList<>();
        for (Tournament tournament : tournamentPage) {
            responses.add(toTournamentResponse(tournament));
        }
        return new PagedResponse<>(
                responses,
                tournamentPage.getNumber(),
                tournamentPage.getSize(),
                tournamentPage.getTotalElements(),
                tournamentPage.getTotalPages(),
                tournamentPage.isLast()
        );
    }

    public TournamentDetailResponse getBySlug(String slug) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElse(null);
        Tournament tournament = tournamentRepository.findBySlug(slug).orElseThrow(() -> new InvalidDataException("Tournament not found"));
        return tournamentDetailResponse(tournament, account);
    }

    public List<TournamentResponse> getNearestTournaments(int top) {
        Coordinate coordinate = resolveCurrentUserCoordinate();
        validateCoordinate(coordinate.latitude(), coordinate.longitude());
        int limit = normalizeTop(top);

        Pageable pageable = PageRequest.of(0, limit);
        List<Tournament> tournaments = tournamentRepository.findNearestTournaments(
                TournamentStatus.CANCELLED,
                coordinate.latitude(),
                coordinate.longitude(),
                pageable
        );

        return tournaments.stream()
                .map(this::toTournamentResponse)
                .toList();
    }

    public TournamentResponse toTournamentResponse(Tournament tournament) {
        List<TournamentCategory> tournamentCategories =
                tournament.getCategories() != null ? tournament.getCategories() : new ArrayList<>();
        List<TournamentCategoryResponse> tournamentCategoryResponses = new ArrayList<>();
        for (TournamentCategory tournamentCategory : tournamentCategories) {
            TournamentCategoryResponse tournamentCategoryResponse = TournamentCategoryResponse.builder()
                    .category(tournamentCategory.getCategory())
                    .id(tournamentCategory.getId())
                    .maxParticipants(tournamentCategory.getMaxParticipants())
                    .build();
            tournamentCategoryResponses.add(tournamentCategoryResponse);
        }
        return TournamentResponse.builder()
                .createdAt(tournament.getCreatedAt())
                .slug(tournament.getSlug())
                .createdBy(tournament.getCreatedBy())
                .status(tournament.getStatus())
                .participationType(tournament.getParticipationType())
                .categories(tournamentCategoryResponses)
                .bannerUrl(fileStorageService.getFileUrl(tournament.getBannerUrl(), "/tournament"))
                .logoUrl(fileStorageService.getFileUrl(tournament.getLogoUrl(), "/tournament"))
                .location(tournament.getLocation())
                .facility(tournament.getFacility() != null ? toFacilityResponse(tournament.getFacility()) : null)
                .registrationEndDate(tournament.getRegistrationEndDate())
                .registrationStartDate(tournament.getRegistrationStartDate())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .id(tournament.getId())
                .name(tournament.getName())
                .description(tournament.getDescription())
                .teamMatchFormat(tournament.getTeamMatchFormat())
                .clubRegistrationFee(tournament.getClubRegistrationFee())
                .minClubRosterSize(tournament.getMinClubRosterSize())
                .maxClubRosterSize(tournament.getMaxClubRosterSize())
                .maxClubs(tournament.getMaxClubs())
                .build();
    }

    public TournamentDetailResponse tournamentDetailResponse(Tournament tournament, Account account){
        List<TournamentCategory> tournamentCategories =
                tournament.getCategories() != null ? tournament.getCategories() : new ArrayList<>();
        List<TournamentCategoryDetailResponse> tournamentCategoryResponses = new ArrayList<>();
        List<TournamentPlayerResponse> tournamentPlayerResponses = new ArrayList<>();
        for (TournamentCategory tournamentCategory : tournamentCategories) {
            boolean isDouble = tournamentCategory.getCategory() != BadmintonCategoryEnum.MEN_SINGLE && tournamentCategory.getCategory()!= BadmintonCategoryEnum.WOMEN_SINGLE;
            int currentCount =0;
            TournamentPlayerResponse tournamentPlayerResponse = new TournamentPlayerResponse();
            tournamentPlayerResponse.setId(tournamentCategory.getId());
            tournamentPlayerResponse.setCategory(tournamentCategory.getCategory());
            if(isDouble) {
                List<TournamentTeam> tournamentTeams =tournamentCategory.getTeams();
                List<TeamResponse> teamResponses = new ArrayList<>();
                for(TournamentTeam tournamentTeam : tournamentTeams) {
                    if(tournamentTeam.getStatus() == TournamentParticipantEnum.APPROVED) {
                        currentCount++;
                        TeamResponse teamResponse = new TeamResponse();
                        teamResponse.setId(tournamentTeam.getId());
                        teamResponse.setTeamName(tournamentTeam.getTeamName());
                        teamResponse.setSlug1(tournamentTeam.getPlayer1().getUserInfo().getSlug());
                        teamResponse.setSlug2(tournamentTeam.getPlayer2().getUserInfo().getSlug());
                        teamResponse.setAvatarUrl1(fileStorageService.getFileUrl(tournamentTeam.getPlayer1().getUserInfo().getAvatarUrl(), "/avatar"));
                        teamResponse.setAvatarUrl2(fileStorageService.getFileUrl(tournamentTeam.getPlayer2().getUserInfo().getAvatarUrl(), "/avatar"));
                        teamResponses.add(teamResponse);
                    }
                }
                tournamentPlayerResponse.setTeams(teamResponses);
            }
            else {
                List<TournamentParticipant> tournamentParticipants =tournamentCategory.getParticipants();
                List<PlayerResponse> playerResponses = new ArrayList<>();
                for(TournamentParticipant tournamentParticipant : tournamentParticipants) {
                    if(tournamentParticipant.getStatus() == TournamentParticipantEnum.APPROVED) {
                        currentCount++;
                        PlayerResponse playerResponse = new PlayerResponse();
                        playerResponse.setId(tournamentParticipant.getId());
                        playerResponse.setName(tournamentParticipant.getDisplayName());
                        playerResponse.setSlug(tournamentParticipant.getAccount().getUserInfo().getSlug());
                        playerResponse.setAvatarUrl(fileStorageService.getFileUrl(tournamentParticipant.getAccount().getUserInfo().getAvatarUrl(), "/avatar"));
                        playerResponses.add(playerResponse);
                    }
                }
                tournamentPlayerResponse.setPlayers(playerResponses);
            }
            tournamentPlayerResponses.add(tournamentPlayerResponse);
            TournamentParticipant tournamentParticipant = null;

            if (account != null) {
                tournamentParticipant = tournamentParticipantRepository.findByAccountAndCategory(account, tournamentCategory);
            }
            TournamentCategoryDetailResponse tournamentCategoryResponse = TournamentCategoryDetailResponse.builder()
                    .category(tournamentCategory.getCategory())
                    .id(tournamentCategory.getId())
                    .currentParticipantCount(currentCount)
                    .maxParticipants(tournamentCategory.getMaxParticipants())
                    .minLevel(tournamentCategory.getMinLevel())
                    .maxLevel(tournamentCategory.getMaxLevel())
                    .participantStatus(tournamentParticipant != null ? tournamentParticipant.getStatus() : null)
                    .build();
            tournamentCategoryResponses.add(tournamentCategoryResponse);
        }
        return TournamentDetailResponse.builder()
                .createdAt(tournament.getCreatedAt())
                .slug(tournament.getSlug())
                .createdBy(tournament.getCreatedBy())
                .status(tournament.getStatus())
                .participationType(tournament.getParticipationType())
                .rules(tournament.getRules())
                .categories(tournamentCategoryResponses)
                .bannerUrl(fileStorageService.getFileUrl(tournament.getBannerUrl(), "/tournament"))
                .logoUrl(fileStorageService.getFileUrl(tournament.getLogoUrl(), "/tournament"))
                .location(tournament.getLocation())
                .facility(tournament.getFacility() != null ? toFacilityResponse(tournament.getFacility()) : null)
                .registrationEndDate(tournament.getRegistrationEndDate())
                .registrationStartDate(tournament.getRegistrationStartDate())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .id(tournament.getId())
                .name(tournament.getName())
                .players(tournamentPlayerResponses)
                .description(tournament.getDescription())
                .teamMatchFormat(tournament.getTeamMatchFormat())
                .clubRegistrationFee(tournament.getClubRegistrationFee())
                .minClubRosterSize(tournament.getMinClubRosterSize())
                .maxClubRosterSize(tournament.getMaxClubRosterSize())
                .maxClubs(tournament.getMaxClubs())
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

    private void calculateStatus(Tournament tournament) {
        TournamentStatus oldStatus = tournament.getStatus();
        TournamentStatus status =tournament.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(tournament.getRegistrationStartDate()) && now.isBefore(tournament.getRegistrationEndDate())) {
            status = TournamentStatus.REGISTRATION_OPEN;
        } else if (now.isAfter(tournament.getRegistrationEndDate()) && now.isBefore(tournament.getStartDate())) {
            status = TournamentStatus.REGISTRATION_CLOSED;
        } else if (now.isAfter(tournament.getStartDate()) && now.isBefore(tournament.getEndDate())) {
            status = TournamentStatus.IN_PROGRESS;
        } else if (now.isAfter(tournament.getEndDate()) && oldStatus!=TournamentStatus.CANCELLED) {
            status = TournamentStatus.COMPLETED;
        }
        if(oldStatus!=status) {
            tournament.setStatus(status);
            tournamentRepository.save(tournament);
        }
    }
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoUpdateTournamentStatus() {
        System.out.println("Chạy hàm giải đấu");
        List<TournamentStatus> excludedStatuses = List.of(
                TournamentStatus.CANCELLED,
                TournamentStatus.COMPLETED
        );
        List<Tournament> tournaments = tournamentRepository.findAllByStatusNot(excludedStatuses);
        for (Tournament tournament : tournaments) {
            calculateStatus(tournament);
        }
    }


    public Object getAllPartner(String categoryId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        return friendshipService.getAllPartner(account.getId(), categoryId);
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
