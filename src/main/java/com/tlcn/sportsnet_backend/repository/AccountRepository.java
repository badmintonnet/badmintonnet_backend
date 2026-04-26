package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.Account;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    @EntityGraph(attributePaths = {"userInfo"})
    Optional<Account> findByEmail(String email);
    Optional<Account> findByUserInfo_Slug(String slug);
    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"userInfo"})
    @Query("""
            SELECT a FROM Account a
            JOIN a.userInfo ui
            WHERE a.id <> :currentAccountId
              AND ui.isProfileProtected = false
              AND ui.latitude IS NOT NULL
              AND ui.longitude IS NOT NULL
            ORDER BY (
                6371 * acos(
                    cos(radians(:latitude)) * cos(radians(ui.latitude))
                    * cos(radians(ui.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(ui.latitude))
                )
            ) ASC
            """)
    List<Account> findNearestVisibleUsers(
            @Param("currentAccountId") String currentAccountId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            Pageable pageable
    );
}
