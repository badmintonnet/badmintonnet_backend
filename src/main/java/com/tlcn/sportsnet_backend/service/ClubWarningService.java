package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.clubWarning.ClubWarningGetRequest;
import com.tlcn.sportsnet_backend.dto.clubWarning.ClubWarningRequest;
import com.tlcn.sportsnet_backend.dto.clubWarning.ClubWarningResponse;
import com.tlcn.sportsnet_backend.dto.club_event.ClubEventCreateRequest;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubMember;
import com.tlcn.sportsnet_backend.entity.ClubWarning;
import com.tlcn.sportsnet_backend.enums.WarningStatus;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClubWarningService {

    private final AccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubWarningRepository clubWarningRepository;
    private final NotificationService notificationService;
    private final AdminNotificationService adminNotificationService;

    public Object createClubWarning(ClubWarningRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Account owner = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Owner not found"));
        Club club = clubRepository.findById(request.getClubId()).orElseThrow(() -> new InvalidDataException("Club not found"));
        Account account = accountRepository.findById(request.getAccountId()).orElseThrow(() -> new InvalidDataException("Account not found"));

        if(!Objects.equals(club.getOwner().getId(), owner.getId())){
            throw new InvalidDataException("Club owner not match");
        }

        ClubMember clubMember = clubMemberRepository.findClubMemberByAccountAndClub(account, club);
        ClubWarning clubWarning = ClubWarning.builder()
                .clubMember(clubMember)
                .reason(request.getReason())
                .build();
         clubWarningRepository.save(clubWarning);
         notificationService.sendToAccount(account.getEmail(),"Cảnh báo từ "+club.getName(), request.getReason(), "/my-clubs/"+club.getSlug());
         adminNotificationService.notifyAllAdmins(
                 "Thành viên bị cảnh báo",
                 "CLB \"" + club.getName() + "\" đã gửi cảnh báo cho " + account.getEmail() + ": " + request.getReason(),
                 "/admin/clubs"
         );
        return "Warning thành công";

    }

    public List<ClubWarningResponse> getAllClubWarnings(String clubId, String accountId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new InvalidDataException("Club not found"));
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new InvalidDataException("Account not found"));
        ClubMember clubMember = clubMemberRepository.findClubMemberByAccountAndClub(account, club);
        List<ClubWarning> clubWarnings = clubWarningRepository.findAllByClubMemberOrderByCreatedAtDesc(clubMember);
        List<ClubWarningResponse> clubWarningResponses = new ArrayList<>();
        for(ClubWarning warning : clubWarnings){
            clubWarningResponses.add(ClubWarningResponse.builder()
                            .id(warning.getId())
                            .status(warning.getStatus())
                            .reason(warning.getReason())
                            .updatedAt(warning.getUpdatedAt())
                            .createdAt(warning.getCreatedAt())
                    .build());
        }
        return clubWarningResponses;
    }

    public Object revokeClubWarning(String id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account owner = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Owner not found"));
        ClubWarning clubWarning = clubWarningRepository.findById(id).orElseThrow(() -> new InvalidDataException("Club warning not found"));
        clubWarning.setStatus(WarningStatus.REVOKED);
        clubWarningRepository.save(clubWarning);
        return "Warning revoked";
    }
}
