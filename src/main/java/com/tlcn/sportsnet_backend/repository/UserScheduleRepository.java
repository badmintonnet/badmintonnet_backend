package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubEvent;
import com.tlcn.sportsnet_backend.entity.UserSchedule;
import com.tlcn.sportsnet_backend.enums.StatusScheduleEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserScheduleRepository extends JpaRepository<UserSchedule, String> {
    UserSchedule findByAccountIdAndClubEventId(String accountId, String clubEventId);
    Page<UserSchedule> findByAccountId(String accountId, Pageable pageable);
    List<UserSchedule> findTop30ByAccount_IdOrderByStartTimeDesc(String accountId);

    Optional<UserSchedule> findByAccountAndClubEvent(Account account, ClubEvent clubEvent);

    List<UserSchedule> findByAccount_IdAndClubEvent_Club_Id(
            String accountId,
            String clubId
    );



    @Query("""
    SELECT COUNT(us) > 0
    FROM UserSchedule us
    WHERE us.account.id = :accountId
      AND (us.status != 'CANCELLED' OR us.status != 'REJECTED')
      AND (
          us.startTime < :eventEndTime
          AND us.endTime > :eventStartTime
      )
""")
    boolean hasConflictWithEventTime(
            @Param("accountId") String accountId,
            @Param("eventStartTime") LocalDateTime eventStartTime,
            @Param("eventEndTime") LocalDateTime eventEndTime
    );
}
