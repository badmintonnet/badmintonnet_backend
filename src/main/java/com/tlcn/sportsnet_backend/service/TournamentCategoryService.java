package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.account.AccountFriend;
import com.tlcn.sportsnet_backend.dto.facility.FacilityResponse;
import com.tlcn.sportsnet_backend.dto.tournament.TournamentCategoryDetailResponse;
import com.tlcn.sportsnet_backend.dto.tournament_participants.TournamentPartnerInvitationResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.BadmintonCategoryEnum;
import com.tlcn.sportsnet_backend.enums.PaymentStatusEnum;
import com.tlcn.sportsnet_backend.enums.TournamentParticipantEnum;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentCategoryService {
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final FileStorageService fileStorageService;
    private final AccountRepository accountRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final TournamentPartnerInvitationRepository tournamentPartnerInvitationRepository;
    private final FriendshipRepository friendshipRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentPaymentRepository tournamentPaymentRepository;
    private final TournamentMatchRepository tournamentMatchRepository;

    public TournamentCategoryDetailResponse getDetailCategoryById(String categoryId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElse(null);

        TournamentCategory category = tournamentCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        return toResponse(category, account);
    }

    private TournamentCategoryDetailResponse toResponse(TournamentCategory tournamentCategory, Account account) {



        boolean isAdmin = false;
        TournamentParticipant tournamentParticipant = null;

        if (account != null) {
            isAdmin = account.getRoles().stream()
                    .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));

            tournamentParticipant = tournamentParticipantRepository.findByAccountAndCategory(account, tournamentCategory);
        }
        List<TournamentPartnerInvitation> tournamentPartnerInvitations = tournamentPartnerInvitationRepository.findByCategoryAndAccount(tournamentCategory.getId(), account);
        TournamentPartnerInvitationResponse response = null;
        List<TournamentPartnerInvitationResponse> requests = new ArrayList<>();
        for(TournamentPartnerInvitation partnerInvitation : tournamentPartnerInvitations) {
            if(partnerInvitation.getInviter().getEmail().equals(account.getEmail())) {
                response = toInvitationResponse(partnerInvitation, true);
            }else {
                requests.add(toInvitationResponse(partnerInvitation, false));
            }
        }

        boolean isDouble = tournamentCategory.getCategory() != BadmintonCategoryEnum.MEN_SINGLE && tournamentCategory.getCategory()!= BadmintonCategoryEnum.WOMEN_SINGLE;
        int currentCount;
        if(isDouble) {
            currentCount = (int) tournamentCategory.getTeams().stream()
                    .filter(p -> p.getStatus() == TournamentParticipantEnum.APPROVED)
                    .count();
        }
        else {
            currentCount = (int) tournamentCategory.getParticipants().stream()
                    .filter(p -> p.getStatus() == TournamentParticipantEnum.APPROVED)
                    .count();
        }
        AccountFriend accountFriend = null;
        Account accountPartner;
        TournamentTeam tournamentTeam = tournamentTeamRepository.findByCategoryAndAccount(tournamentCategory.getId(), account).orElse(null);
        if(tournamentTeam != null) {
        if(tournamentTeam.getPlayer1().getEmail() == account.getEmail() ) {
            accountPartner = tournamentTeam.getPlayer2();
        }
        else{
            accountPartner = tournamentTeam.getPlayer1();
        }
        accountFriend = AccountFriend.builder()
                .id(accountPartner.getId())
                .avatarUrl(fileStorageService.getFileUrl(accountPartner.getUserInfo().getAvatarUrl(), "/avatar"))
                .fullName(accountPartner.getUserInfo().getFullName())
                .skillLevel(playerRatingRepository.findByAccount(accountPartner)
                        .map(PlayerRating::getSkillLevel)
                        .orElse("Not rated"))
                .slug(accountPartner.getUserInfo().getSlug())
                .build();}
        boolean paid = false;

        if (tournamentParticipant != null) {
            paid = tournamentPaymentRepository.existsByParticipantAndStatus(
                    tournamentParticipant,
                    PaymentStatusEnum.SUCCESS
            );
        }
        else {
            paid = tournamentPaymentRepository.existsByTeamAndStatus(
                    tournamentTeam,
                    PaymentStatusEnum.SUCCESS
            );
        }

        boolean isScheduled = true;

        List<TournamentMatch> matches = tournamentMatchRepository.findAll()
                .stream()
                .filter(m -> m.getCategory().getId().equals(tournamentCategory.getId()))
                .toList();

        if(matches.isEmpty() ) {
            isScheduled = false;
        }

        return TournamentCategoryDetailResponse.builder()
                .id(tournamentCategory.getId())
                .tournamentName(tournamentCategory.getTournament().getName())
                .facility(tournamentCategory.getTournament().getFacility() != null ? toFacilityResponse(tournamentCategory.getTournament().getFacility()) : null)
                .startDate(tournamentCategory.getTournament().getStartDate())
                .endDate(tournamentCategory.getTournament().getEndDate())
                .category(tournamentCategory.getCategory())
                .minLevel(tournamentCategory.getMinLevel())
                .maxLevel(tournamentCategory.getMaxLevel())
                .maxParticipants(tournamentCategory.getMaxParticipants())
                .currentParticipantCount(currentCount)
                .registrationFee(tournamentCategory.getRegistrationFee())
                .description(tournamentCategory.getDescription())
                .rules(tournamentCategory.getRules())
                .firstPrize(tournamentCategory.getFirstPrize())
                .secondPrize(tournamentCategory.getSecondPrize())
                .thirdPrize(tournamentCategory.getThirdPrize())
                .registrationStartDate(tournamentCategory.getTournament().getRegistrationStartDate())
                .registrationEndDate(tournamentCategory.getTournament().getRegistrationEndDate())
                .isDouble(isDouble)
                .participantStatus(tournamentParticipant != null ? tournamentParticipant.getStatus() : tournamentTeam!= null ? tournamentTeam.getStatus() : null)
                .admin(isAdmin)
                .response(response)
                .requests(requests)
                .partner(accountFriend)
                .paid(paid)
                .scheduled(isScheduled)
                .build();
    }

    private TournamentPartnerInvitationResponse toInvitationResponse(TournamentPartnerInvitation partnerInvitation, boolean isInviter) {
        TournamentPartnerInvitationResponse tournamentPartnerInvitationResponse = new TournamentPartnerInvitationResponse();
        tournamentPartnerInvitationResponse.setId(partnerInvitation.getId());
        tournamentPartnerInvitationResponse.setCreatedAt(partnerInvitation.getCreatedAt());
        tournamentPartnerInvitationResponse.setStatus(partnerInvitation.getStatus());
        tournamentPartnerInvitationResponse.setMessage(partnerInvitation.getMessage());
        if(isInviter) {
            Account invitee = partnerInvitation.getInvitee();
            tournamentPartnerInvitationResponse.setInvitee(AccountFriend.builder()
                    .id(invitee.getId())
                    .avatarUrl(fileStorageService.getFileUrl(invitee.getUserInfo().getAvatarUrl(), "/avatar"))
                    .fullName(invitee.getUserInfo().getFullName())
                    .skillLevel(playerRatingRepository.findByAccount(invitee)
                            .map(PlayerRating::getSkillLevel)
                            .orElse("Not rated"))
                    .slug(invitee.getUserInfo().getSlug())
                    .mutualFriends(friendshipRepository.countMutualFriends(partnerInvitation.getInviter().getId(), invitee.getId()))
                    .build());
        }
        else {
            Account inviter = partnerInvitation.getInviter();
            tournamentPartnerInvitationResponse.setInviter(AccountFriend.builder()
                    .id(inviter.getId())
                    .avatarUrl(fileStorageService.getFileUrl(inviter.getUserInfo().getAvatarUrl(), "/avatar"))
                    .fullName(inviter.getUserInfo().getFullName())
                    .skillLevel(playerRatingRepository.findByAccount(inviter)
                            .map(PlayerRating::getSkillLevel)
                            .orElse("Not rated"))
                    .slug(inviter.getUserInfo().getSlug())
                    .mutualFriends(friendshipRepository.countMutualFriends(partnerInvitation.getInvitee().getId(), inviter.getId()))
                    .build());
        }
        tournamentPartnerInvitationResponse.setSend(isInviter);
        return tournamentPartnerInvitationResponse;
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
}
