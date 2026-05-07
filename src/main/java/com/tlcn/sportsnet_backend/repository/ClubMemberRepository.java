package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Club;
import com.tlcn.sportsnet_backend.entity.ClubMember;
import com.tlcn.sportsnet_backend.enums.ClubMemberStatusEnum;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, String> {
    boolean existsByClubAndAccount(Club club, Account account);
    boolean existsByClubAndAccountAndStatus(Club club, Account account, ClubMemberStatusEnum status);

    // Basic - khong fetch account
    ClubMember findByClubAndAccount(Club club, Account account);

    // Fetch voi account va userInfo
    @Query("SELECT cm FROM ClubMember cm LEFT JOIN FETCH cm.account a LEFT JOIN FETCH a.userInfo WHERE cm.club = :club AND cm.account = :account")
    ClubMember findByClubAndAccountWithAccount(@Param("club") Club club, @Param("account") Account account);
    @Query("SELECT cm FROM ClubMember cm WHERE cm.club.id = :clubId")
    Page<ClubMember> findPagedByClubId(String clubId, Pageable pageable);
    @Query("""
        SELECT cm FROM ClubMember cm 
        WHERE cm.club.id = :clubId 
          AND (:status IS NULL OR cm.status = :status)
    """)
    Page<ClubMember> findPagedByClubIdAndStatus(
            @Param("clubId") String clubId,
            @Param("status") ClubMemberStatusEnum status,
            Pageable pageable
    );
    @Query("SELECT cm FROM ClubMember cm WHERE cm.club.id = :clubId")
    List<ClubMember> findByClubId(String clubId);
    @Query("SELECT cm FROM ClubMember cm WHERE cm.club.id = :clubId AND cm.status = :status")
    List<ClubMember> findByClubIdAndStatus(@Param("clubId") String clubId,
                                           @Param("status") ClubMemberStatusEnum status);
    @Query("SELECT COUNT(cm) FROM ClubMember cm WHERE cm.club.id = :clubId AND cm.status = :status")
    long countByClubIdAndStatus(@Param("clubId") String clubId,
                                @Param("status") ClubMemberStatusEnum status);
    @Query("""
SELECT cm.club.id, COUNT(cm)
FROM ClubMember cm
WHERE cm.club.id IN :clubIds
AND cm.status = :status
GROUP BY cm.club.id
""")
    List<Object[]> countMembersByClubIds(
            @Param("clubIds") List<String> clubIds,
            @Param("status") ClubMemberStatusEnum status
    );
    ClubMember findClubMemberByAccountAndClub(Account account, Club club);

    Optional<ClubMember> findClubMemberByAccount_IdAndClub_Id(String accountId, String clubId);

    @Query("SELECT COUNT(cm) FROM ClubMember cm WHERE cm.account = :account AND cm.ratingVerified = true ")
    long countByAccountAndRatingVerifiedTrue(Account account);

    @Modifying
    @Transactional
    @Query("""
    UPDATE ClubMember cm
    SET cm.ratingVerified = false
    WHERE cm.account = :account
      AND cm.ratingVerified = true
""")
    int resetRatingVerifiedByAccount(Account account);

    @Query("SELECT cm FROM ClubMember cm WHERE cm.club.id = :clubId AND cm.account.id = :accountId")
    ClubMember findByClubIdAndAccountId(@Param("clubId") String clubId, @Param("accountId") String accountId);

    // Fetch ClubMember with account va userInfo
    @Query("SELECT cm FROM ClubMember cm LEFT JOIN FETCH cm.account a LEFT JOIN FETCH a.userInfo WHERE cm.id = :id")
    Optional<ClubMember> findByIdWithAccount(@Param("id") String id);

    // Fetch all ClubMembers of a club with account va userInfo
    @Query("SELECT cm FROM ClubMember cm LEFT JOIN FETCH cm.account a LEFT JOIN FETCH a.userInfo WHERE cm.club.id = :clubId")
    List<ClubMember> findByClubIdWithAccount(@Param("clubId") String clubId);
}
