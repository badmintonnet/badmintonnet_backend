package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.account.AccountRegisterRequest;
import com.tlcn.sportsnet_backend.dto.account.AccountResponse;
import com.tlcn.sportsnet_backend.dto.account.UpdateProfileRequest;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.Role;
import com.tlcn.sportsnet_backend.entity.UserInfo;
import com.tlcn.sportsnet_backend.enums.ClubMemberRoleEnum;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import com.tlcn.sportsnet_backend.enums.ClubStatusEnum;
import com.tlcn.sportsnet_backend.error.UnauthorizedException;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final FriendshipRepository friendshipRepository;
    private final NominatimService nominatimService;

    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    public AccountResponse getAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return toResponse(account);
    }

    public AccountResponse getOtherAccount(String slug) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account loginAccount = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        Account account = accountRepository.findByUserInfo_Slug(slug).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return toOtherAccountResponse(account,loginAccount);
    }
    public Account registerAccount(AccountRegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại, vui lòng chọn email khác.");
        }

        Role role = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Role USER không tồn tại"));

        Account newAccount = Account.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(role))
                .reputationScore(100)
                .build();

        UserInfo userInfo = UserInfo.builder()
                .fullName(request.getFullName())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .address(request.getAddress())
                .account(newAccount)
                .build();
        

        newAccount.setUserInfo(userInfo);
        newAccount = accountRepository.save(newAccount);
        System.out.println("Đăng kí tài khoản: " + newAccount.getEmail());

        return newAccount;

    }

    public AccountResponse updateProfile(UpdateProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        UserInfo userInfo = account.getUserInfo();
        userInfo.setFullName(request.getFullName());
        userInfo.setGender(request.getGender());
        userInfo.setAddress(request.getAddress());
//        nominatimService.geocodeAddress(request.getAddress())
//                .ifPresentOrElse(
//                        coordinates -> {
//                            userInfo.setLatitude(coordinates.getLatitude());
//                            userInfo.setLongitude(coordinates.getLongitude());
//                        },
//                        () -> {
//                            log.warn("Không thể geocode địa chỉ: {}", request.getAddress());
//                            userInfo.setLatitude(null);
//                            userInfo.setLongitude(null);
//                        }
//                );
        userInfo.setLatitude(request.getLatitude() != null ? Double.parseDouble(request.getLatitude()) : null);
        userInfo.setLongitude(request.getLongitude() != null ? Double.parseDouble(request.getLongitude()) : null);
        userInfo.setPhone(request.getPhone());
        userInfo.setBio(request.getBio());
        userInfo.setBirthDate(request.getBirthDate());
        if(request.getAvatarUrl() != null) {
            userInfo.setAvatarUrl(request.getAvatarUrl());
        }

        account.setUserInfo(userInfo);

        account = accountRepository.save(account);

        return toResponse(account);
    }

    public AccountResponse toResponse(Account account) {
        List<Club> clubs = clubRepository.findAllByOwnerAndStatusOrderByReputationDesc(account, ClubStatusEnum.ACTIVE);
        List<Club> myClubs = clubRepository.findClubsForUserAndStatus(account, ClubMemberStatusEnum.APPROVED, ClubMemberRoleEnum.MEMBER, ClubStatusEnum.ACTIVE);
        List<AccountResponse.OwnerClub> ownerClubs = new ArrayList<>();
        List<AccountResponse.OwnerClub> myOwnerClubs = new ArrayList<>();
        for(Club club : clubs){
            ownerClubs.add(new AccountResponse.OwnerClub(club.getName(), club.getSlug(), fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo") ));
        }
        for(Club club : myClubs){
            myOwnerClubs.add(new AccountResponse.OwnerClub(club.getName(), club.getSlug(), fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo") ));
        }
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .fullName(account.getUserInfo().getFullName())
                .gender(account.getUserInfo().getGender())
                .address(account.getUserInfo().getAddress())
                .birthDate(account.getUserInfo().getBirthDate())
                .phone(account.getUserInfo().getPhone())
                .bio(account.getUserInfo().getBio())
                .latitude(account.getUserInfo().getLatitude())
                .longitude(account.getUserInfo().getLongitude())
                .avatarUrl(fileStorageService.getFileUrl(account.getUserInfo().getAvatarUrl(), "/avatar"))
                .enabled(account.isEnabled())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .createdBy(account.getCreatedBy())
                .updatedBy(account.getUpdatedBy())
                .reputationScore(account.getReputationScore())
                .totalParticipatedEvents(account.getTotalParticipatedEvents())
                .ownerClubs(ownerClubs)
                .myClubs(myOwnerClubs)
                .isProfileProtected(account.getUserInfo().isProfileProtected())
                .build();
    }
    public Object protectProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        UserInfo userInfo = account.getUserInfo();
        userInfo.setProfileProtected(!userInfo.isProfileProtected());
        account.setUserInfo(userInfo);
        accountRepository.save(account);
        return "Chỉnh sửa thành công";
    }
    public List<String> getAllClubID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return clubRepository.findActiveMemberClubIds(account, ClubMemberRoleEnum.MEMBER, ClubMemberStatusEnum.APPROVED);
    }

    public AccountResponse toOtherAccountResponse(Account account, Account loginAccount) {
        boolean areFriend = friendshipRepository.areFriends(account.getId(), loginAccount.getId());
        List<Club> clubs = clubRepository.findAllByOwnerAndStatusOrderByReputationDesc(account, ClubStatusEnum.ACTIVE);
        List<Club> myClubs = clubRepository.findClubsForUserAndStatus(account, ClubMemberStatusEnum.APPROVED, ClubMemberRoleEnum.MEMBER, ClubStatusEnum.ACTIVE);
        List<AccountResponse.OwnerClub> ownerClubs = new ArrayList<>();
        List<AccountResponse.OwnerClub> myOwnerClubs = new ArrayList<>();
        for(Club club : clubs){
            ownerClubs.add(new AccountResponse.OwnerClub(club.getName(), club.getSlug(), fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo") ));
        }
        for(Club club : myClubs){
            myOwnerClubs.add(new AccountResponse.OwnerClub(club.getName(), club.getSlug(), fileStorageService.getFileUrl(club.getLogoUrl(), "/club/logo") ));
        }
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .fullName(account.getUserInfo().getFullName())
                .gender(account.getUserInfo().getGender())
                .address(account.getUserInfo().getAddress())
                .birthDate(account.getUserInfo().getBirthDate())
                .phone(account.getUserInfo().getPhone())
                .bio(account.getUserInfo().getBio())
                .latitude(account.getUserInfo().getLatitude())
                .longitude(account.getUserInfo().getLongitude())
                .avatarUrl(fileStorageService.getFileUrl(account.getUserInfo().getAvatarUrl(), "/avatar"))
                .enabled(account.isEnabled())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .createdBy(account.getCreatedBy())
                .updatedBy(account.getUpdatedBy())
                .reputationScore(account.getReputationScore())
                .totalParticipatedEvents(account.getTotalParticipatedEvents())
                .ownerClubs(ownerClubs)
                .myClubs(myOwnerClubs)
                .isProfileProtected(!areFriend && account.getUserInfo().isProfileProtected())
                .build();
    }


}
