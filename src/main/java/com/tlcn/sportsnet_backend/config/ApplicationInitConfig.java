package com.tlcn.sportsnet_backend.config;

import com.tlcn.sportsnet_backend.entity.Account;
import com.tlcn.sportsnet_backend.entity.Role;
import com.tlcn.sportsnet_backend.entity.UserInfo;
import com.tlcn.sportsnet_backend.repository.AccountRepository;
import com.tlcn.sportsnet_backend.repository.RoleRepository;
import com.tlcn.sportsnet_backend.service.ClubEventService;
import com.tlcn.sportsnet_backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;


@Component
@RequiredArgsConstructor
public class ApplicationInitConfig implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClubEventService clubEventService;
    private final TournamentService tournamentService;

    @Override
    public void run(ApplicationArguments args) {
        initRolesAndAdminUser();
    }

    private void initRolesAndAdminUser() {
        createRoleIfNotExists("ROLE_ADMIN");
        createRoleIfNotExists("ROLE_USER");
        createRoleIfNotExists("ROLE_CLUB_OWNER");

        if (accountRepository.findByEmail("admin@gmail.com").isEmpty()) {
            Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow(
                    () -> new RuntimeException("ADMIN không tồn tại"));



            Account admin = Account.builder()
                    .email("admin@gmail.com")
                    .password(passwordEncoder.encode("123456"))
                    .roles(Set.of(adminRole))
                    .verified(true)
                    .build();

            admin = accountRepository.save(admin);

            UserInfo userInfo = UserInfo.builder()
                    .fullName("ADMIN")
                    .account(admin)
                    .build();

            admin.setUserInfo(userInfo);

            accountRepository.save(admin);


        }
        clubEventService.autoUpdateEventStatus();
        tournamentService.autoUpdateTournamentStatus();
    }

    private void createRoleIfNotExists(String roleName) {
        roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
    }

}
