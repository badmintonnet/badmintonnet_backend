package com.tlcn.sportsnet_backend.service;

import com.tlcn.sportsnet_backend.dto.schedule.ScheduleResponse;
import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.ClubEvent;
import com.tlcn.sportsnet_backend.entity.ClubEventParticipant;
import com.tlcn.sportsnet_backend.entity.UserSchedule;
import com.tlcn.sportsnet_backend.enums.StatusScheduleEnum;
import com.tlcn.sportsnet_backend.error.InvalidDataException;
import com.tlcn.sportsnet_backend.payload.response.PagedResponse;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.ClubEventParticipantRepository;
import com.tlcn.sportsnet_backend.repository.ClubEventRepository;
import com.tlcn.sportsnet_backend.repository.UserScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserScheduleService {
    private final ClubEventRepository clubEventRepository;
    private final ClubEventParticipantRepository participantRepository;
    private final UserScheduleRepository userScheduleRepository;
    private final AccountRepository accountRepository;
    public void createAll() {
        List<ClubEvent> clubEvents = clubEventRepository.findAll();
        for (ClubEvent clubEvent : clubEvents) {
            List<ClubEventParticipant> clubEventParticipants = participantRepository.findAllByClubEventOrderByJoinedAtDesc(clubEvent);
            for (ClubEventParticipant clubEventParticipant : clubEventParticipants) {
                createScheduleByClubEvent(clubEvent, clubEventParticipant);
            }
        }
    }

    public void createScheduleByClubEvent(ClubEvent clubEvent, ClubEventParticipant clubEventParticipant) {

        UserSchedule userSchedule = UserSchedule.builder()
                .account(clubEventParticipant.getParticipant())
                .clubEvent(clubEvent)
                .name(clubEvent.getTitle())
                .startTime(clubEvent.getStartTime())
                .endTime(clubEvent.getEndTime())
                .status(clubEventParticipant.getStatus().toStatusEnum())
                .createdAt(clubEventParticipant.getJoinedAt())
                .createdBy(clubEventParticipant.getParticipant().getEmail())
                .build();
        userScheduleRepository.save(userSchedule);
    }


    public PagedResponse<ScheduleResponse> getSchedule(int page, int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Account account = accountRepository.findByEmail(authentication.getName()).orElseThrow(() -> new InvalidDataException("Account not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").descending());

        Page<UserSchedule> schedules = userScheduleRepository.findByAccountId(account.getId(), pageable);

        List<ScheduleResponse> content = schedules.getContent().stream()
                .map(schedule -> toScheduleResponse(schedule, account))
                .toList();

        return new PagedResponse<>(
                content,
                schedules.getNumber(),
                schedules.getSize(),
                schedules.getTotalElements(),
                schedules.getTotalPages(),
                schedules.isLast()
        );
    }

    public ScheduleResponse toScheduleResponse(UserSchedule userSchedule, Account account) {
        StatusScheduleEnum oldStatus = userSchedule.getStatus();
        ClubEvent clubEvent = userSchedule.getClubEvent();
        ClubEventParticipant clubEventParticipant = participantRepository.findByClubEvent_IdAndParticipant(clubEvent.getId(), account).orElseThrow(() -> new InvalidDataException("Participant not found"));

        StatusScheduleEnum statusScheduleEnum = resolveScheduleStatus(userSchedule, clubEventParticipant);
        if(oldStatus != statusScheduleEnum) {
            userSchedule.setStatus(statusScheduleEnum);
            userScheduleRepository.save(userSchedule);
        }
        return ScheduleResponse.builder()
                .id(userSchedule.getId())
                .createdAt(userSchedule.getCreatedAt())
                .endTime(userSchedule.getEndTime())
                .startTime(userSchedule.getStartTime())
                .status(statusScheduleEnum)
                .name(userSchedule.getName())
                .slug(clubEvent.getSlug())
                .build();
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoUpdateScheduleStatus() {
        List<UserSchedule> schedules = userScheduleRepository.findAll();

        for (UserSchedule schedule : schedules) {
            ClubEvent clubEvent = schedule.getClubEvent();
            Account account = schedule.getAccount();
            ClubEventParticipant participant = participantRepository
                    .findByClubEvent_IdAndParticipant(clubEvent.getId(), account)
                    .orElse(null);

            if (participant == null) {
                continue;
            }

            StatusScheduleEnum newStatus = resolveScheduleStatus(schedule, participant);
            if (schedule.getStatus() != newStatus) {
                schedule.setStatus(newStatus);
                userScheduleRepository.save(schedule);
            }
        }
    }

    private StatusScheduleEnum resolveScheduleStatus(UserSchedule userSchedule, ClubEventParticipant clubEventParticipant) {
        LocalDateTime now = LocalDateTime.now();
        StatusScheduleEnum status = clubEventParticipant.getStatus().toStatusEnum();

        if (now.isBefore(userSchedule.getEndTime()) && now.isAfter(userSchedule.getStartTime())
                || now.isEqual(userSchedule.getEndTime())
                || now.isEqual(userSchedule.getStartTime())) {
            status = StatusScheduleEnum.ONGOING;
        }

        return status;
    }
}
