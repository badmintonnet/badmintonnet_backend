package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.NotificationMessage;
import com.tlcn.sportsnet_backend.entity.*;
import com.tlcn.sportsnet_backend.enums.NotificationTypeEnum;
import com.tlcn.sportsnet_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AccountRepository accountRepository;
    private final NotificationRepository notificationRepository;

    public void notifyAllAdmins(String title, String content, String link) {
        List<Account> admins = accountRepository.findAll().stream()
                .filter(a -> a.isEnabled() && a.getRoles().stream()
                        .anyMatch(r -> r.getName().equals("ROLE_ADMIN")))
                .toList();

        if (admins.isEmpty()) return;

        Instant now = Instant.now();
        Notification notification = Notification.builder()
                .title(title)
                .content(content)
                .link(link)
                .createdAt(now)
                .type(NotificationTypeEnum.CLUB)
                .recipients(new ArrayList<>())
                .build();

        for (Account admin : admins) {
            NotificationRecipient recipient = NotificationRecipient.builder()
                    .notification(notification)
                    .account(admin)
                    .isRead(false)
                    .build();
            notification.getRecipients().add(recipient);
        }

        notificationRepository.save(notification);

        NotificationMessage msg = new NotificationMessage(
                notification.getId(), title, content, link, now, false);
        for (NotificationRecipient r : notification.getRecipients()) {
            messagingTemplate.convertAndSend("/topic/account/" + r.getAccount().getId(), msg);
        }
    }
}
