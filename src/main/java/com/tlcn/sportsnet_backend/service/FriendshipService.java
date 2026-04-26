package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.account.AccountFriend;
import com.tlcn.sportsnet_backend.dto.friend.FriendRequest;
import com.tlcn.sportsnet_backend.dto.friend.FriendResponse;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.ChatRole;
import com.tlcn.sportsnet_backend.enums.ConversationType;
import com.tlcn.sportsnet_backend.enums.FriendStatusEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final AccountRepository accountRepository;
    private final FileStorageService fileStorageService;
    private final PlayerRatingRepository playerRatingRepository;
    private final NotificationService notificationService;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final TournamentParticipantService tournamentParticipantService;
    private final TournamentTeamRepository tournamentTeamRepository;

    public FriendResponse getRelationship(String accountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account currentAccount = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new InvalidDataException("Account not found"));

        Account otherAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidDataException("Other user not found"));

        if (currentAccount.getId().equals(otherAccount.getId())) {
            throw new InvalidDataException("Cannot check relationship with yourself");
        }

        Optional<Friendship> relation = friendshipRepository.findBetween(currentAccount, otherAccount);

        if (relation.isEmpty()) {
            // Không tạo mới nữa, chỉ trả về NONE
            return FriendResponse.builder()
                    .status(FriendStatusEnum.NONE)
                    .requester(FriendResponse.UserSummary.builder()
                            .id(currentAccount.getId())
                            .email(currentAccount.getEmail())
                            .fullName(currentAccount.getUserInfo() != null
                                    ? currentAccount.getUserInfo().getFullName()
                                    : null)
                            .build())
                    .receiver(FriendResponse.UserSummary.builder()
                            .id(otherAccount.getId())
                            .email(otherAccount.getEmail())
                            .fullName(otherAccount.getUserInfo() != null
                                    ? otherAccount.getUserInfo().getFullName()
                                    : null)
                            .build())
                    .build();
        }

        return toResponse(relation.get());
    }

    @Transactional
    public FriendResponse sendFriendRequest(FriendRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account requester = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Account receiver = accountRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new InvalidDataException("Receiver not found"));

        if (requester.getId().equals(receiver.getId())) {
            throw new InvalidDataException("Cannot send friend request to yourself");
        }

        Optional<Friendship> existing = friendshipRepository.findBetween(requester, receiver);

        Friendship friendship;
        if (existing.isPresent()) {
            friendship = existing.get();
            if (friendship.getStatus() == FriendStatusEnum.NONE || friendship.getStatus() == FriendStatusEnum.REJECTED) {
                friendship.setRequester(requester);
                friendship.setReceiver(receiver);
                friendship.setStatus(FriendStatusEnum.PENDING);
                friendship = friendshipRepository.save(friendship);
            } else {
                throw new InvalidDataException("Friendship already exists with status: " + friendship.getStatus());
            }
        } else {
            friendship = Friendship.builder()
                    .requester(requester)
                    .receiver(receiver)
                    .status(FriendStatusEnum.PENDING)
                    .build();
            friendship = friendshipRepository.save(friendship);
        }
        notificationService.sendToAccount(receiver.getEmail(),"Lời mời kết bạn!" ,requester.getUserInfo().getFullName() + " đã lời mời kết bạn đến bạn ","/profile/" + requester.getUserInfo().getSlug());
        return toResponse(friendship);
    }

    private FriendResponse toResponse(Friendship friendship) {
        return FriendResponse.builder()
                .id(friendship.getId())
                .status(friendship.getStatus())
                .createdAt(friendship.getCreatedAt())
                .requester(FriendResponse.UserSummary.builder()
                        .id(friendship.getRequester().getId())
                        .email(friendship.getRequester().getEmail())
                        .fullName(friendship.getRequester().getUserInfo() != null
                                ? friendship.getRequester().getUserInfo().getFullName()
                                : null)
                        .build())
                .receiver(FriendResponse.UserSummary.builder()
                        .id(friendship.getReceiver().getId())
                        .email(friendship.getReceiver().getEmail())
                        .fullName(friendship.getReceiver().getUserInfo() != null
                                ? friendship.getReceiver().getUserInfo().getFullName()
                                : null)
                        .build())
                .build();
    }

    public FriendResponse acceptFriendRequest(String requesterId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account receiver = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Account requester = accountRepository.findById(requesterId)
                .orElseThrow(() -> new InvalidDataException("Requester not found"));

        Friendship friendship = friendshipRepository.findBetween(requester, receiver)
                .orElseThrow(() -> new InvalidDataException("Friend request not found"));

        if (!friendship.getReceiver().getId().equals(receiver.getId())) {
            throw new RuntimeException("You are not allowed to accept this request");
        }
        if (friendship.getStatus() != FriendStatusEnum.PENDING) {
            throw new InvalidDataException("Invalid request state: " + friendship.getStatus());
        }

        friendship.setStatus(FriendStatusEnum.ACCEPTED);
        friendship = friendshipRepository.save(friendship);

        Optional<Conversation> existing = conversationRepository.findPrivateBetweenUsers(requester.getId(), receiver.getId(), ConversationType.PRIVATE);
        if (existing.isEmpty()) {
            Conversation conversation = new Conversation();
            conversation.setType(ConversationType.PRIVATE);

            // Lưu conversation
            conversation = conversationRepository.save(conversation);

            // Thêm participants
            ConversationParticipant p1 = new ConversationParticipant();
            p1.setConversation(conversation);
            p1.setAccount(requester);
            p1.setRole(ChatRole.MEMBER);

            ConversationParticipant p2 = new ConversationParticipant();
            p2.setConversation(conversation);
            p2.setAccount(receiver);
            p2.setRole(ChatRole.MEMBER);

            conversationParticipantRepository.saveAll(List.of(p1, p2));
        }


        notificationService.sendToAccount(requester.getEmail(),"Chấp nhận lời mời kết bạn!" ,receiver.getUserInfo().getFullName() + " đã chấp nhận lời mời kết bạn của bạn ","/profile/" + receiver.getUserInfo().getSlug());

        return toResponse(friendship);
    }

    public FriendResponse rejectFriendRequest(String requesterId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account receiver = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Account requester = accountRepository.findById(requesterId)
                .orElseThrow(() -> new InvalidDataException("Requester not found"));

        Friendship friendship = friendshipRepository.findBetween(requester, receiver)
                .orElseThrow(() -> new InvalidDataException("Friend request not found"));

        if (!friendship.getReceiver().getId().equals(receiver.getId())) {
            throw new RuntimeException("You are not allowed to reject this request");
        }
        if (friendship.getStatus() != FriendStatusEnum.PENDING) {
            throw new InvalidDataException("Invalid request state: " + friendship.getStatus());
        }

        friendship.setStatus(FriendStatusEnum.REJECTED);
        friendship = friendshipRepository.save(friendship);

        notificationService.sendToAccount(requester.getEmail(),"Từ chối lời mời kết bạn!" ,receiver.getUserInfo().getFullName() + " đã từ chối lời mời kết bạn của bạn ","/profile/" + receiver.getUserInfo().getSlug());
        return toResponse(friendship);
    }

    public List<AccountFriend> getAllFriends(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Friendship> friendships = friendshipRepository.findAllFriends(account);

        // Chuyển về list Account bạn bè
        List<Account> listFriendAccount = friendships.stream()
                .map(f -> f.getRequester().equals(account) ? f.getReceiver() : f.getRequester())
                .toList();

        return listFriendAccount.stream()
                .map(x -> AccountFriend.builder()
                        .id(x.getId())
                        .avatarUrl(fileStorageService.getFileUrl(x.getUserInfo().getAvatarUrl(), "/avatar"))
                        .fullName(x.getUserInfo().getFullName())
                        .skillLevel(playerRatingRepository.findByAccount(x)
                                .map(PlayerRating::getSkillLevel)
                                .orElse("Chưa có"))
                        .slug(x.getUserInfo().getSlug())
                        .mutualFriends(friendshipRepository.countMutualFriends(accountId, x.getId()))
                        .build())
                .toList();
    }
    public List<AccountFriend> getAllPartner(String accountId, String categoryId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Friendship> friendships = friendshipRepository.findAllFriends(account);

        // Chuyển về list Account bạn bè
        List<Account> listFriendAccount = friendships.stream()
                .map(f -> f.getRequester().equals(account) ? f.getReceiver() : f.getRequester())
                .toList();
        List<Account> filteredList = listFriendAccount.stream()
                .filter(friend -> !tournamentTeamRepository.existsByAccountAndCategory(categoryId, friend))
                .collect(Collectors.toList());

        return filteredList.stream()
                .map(x -> AccountFriend.builder()
                        .id(x.getId())
                        .avatarUrl(fileStorageService.getFileUrl(x.getUserInfo().getAvatarUrl(), "/avatar"))
                        .fullName(x.getUserInfo().getFullName())
                        .skillLevel(playerRatingRepository.findByAccount(x)
                                .map(PlayerRating::getSkillLevel)
                                .orElse("Chưa có"))
                        .slug(x.getUserInfo().getSlug())
                        .mutualFriends(friendshipRepository.countMutualFriends(accountId, x.getId()))
                        .build())
                .toList();
    }
    public List<AccountFriend> getAllRequesters() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new InvalidDataException("Account not found"));

        List<Friendship> friendships = friendshipRepository.findAllFriendsByReceiverAndStatus(
                account,
                FriendStatusEnum.PENDING,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Account> listAccountRequest = friendships.stream()
                .map(Friendship::getRequester)
                .toList();

        return listAccountRequest.stream()
                .map(x -> AccountFriend.builder()
                        .id(x.getId())
                        .avatarUrl(fileStorageService.getFileUrl(x.getUserInfo().getAvatarUrl(), "/avatar"))
                        .fullName(x.getUserInfo().getFullName())
                        .skillLevel(playerRatingRepository.findByAccount(x)
                                .map(PlayerRating::getSkillLevel)
                                .orElse("Chưa có"))
                        .slug(x.getUserInfo().getSlug())
                        .build())
                .toList();


    }

    public Object unfriend(String userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account userA = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));
        Account userB = accountRepository.findById(userId)
                .orElseThrow(() -> new InvalidDataException("Requester not found"));
        Friendship friendship = friendshipRepository.findBetween(userA,userB).orElseThrow(() -> new InvalidDataException("Friend request not found"));
        friendshipRepository.delete(friendship);
        return "Xóa kết bạn thành công";
    }

    public List<AccountFriend> getNearbyUsers(int top) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account currentAccount = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidDataException("Account not found"));

        Coordinate coordinate = resolveCurrentUserCoordinate(currentAccount);
        validateCoordinate(coordinate.latitude(), coordinate.longitude());
        int limit = normalizeTop(top);

        List<Account> accounts = accountRepository.findNearestVisibleUsers(
                currentAccount.getId(),
                coordinate.latitude(),
                coordinate.longitude(),
                PageRequest.of(0, limit)
        );

        return accounts.stream()
                .map(x -> AccountFriend.builder()
                        .id(x.getId())
                        .avatarUrl(fileStorageService.getFileUrl(x.getUserInfo().getAvatarUrl(), "/avatar"))
                        .fullName(x.getUserInfo().getFullName())
                        .skillLevel(playerRatingRepository.findByAccount(x)
                                .map(PlayerRating::getSkillLevel)
                                .orElse("Chưa có"))
                        .slug(x.getUserInfo().getSlug())
                        .mutualFriends(friendshipRepository.countMutualFriends(currentAccount.getId(), x.getId()))
                        .address(x.getUserInfo().getAddress())
                        .build())
                .toList();
    }

    private Coordinate resolveCurrentUserCoordinate(Account account) {
        UserInfo userInfo = account.getUserInfo();
        if (userInfo == null || userInfo.getLatitude() == null || userInfo.getLongitude() == null) {
            throw new InvalidDataException("Vui long cap nhat dia chi de he thong xac dinh vi tri cua ban");
        }
        return new Coordinate(userInfo.getLatitude(), userInfo.getLongitude());
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

    private record Coordinate(double latitude, double longitude) {}
}
