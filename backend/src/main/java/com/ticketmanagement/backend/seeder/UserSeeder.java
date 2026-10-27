package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class UserSeeder {

    private static final List<SeedAccount> ACCOUNTS = List.of(
            new SeedAccount("admin@example.com", Role.ADMIN),
            new SeedAccount("zika@example.com", Role.SUPPORT_AGENT),
            new SeedAccount("nikola@example.com", Role.SUPPORT_AGENT),
            new SeedAccount("agent3@example.com", Role.SUPPORT_AGENT),
            new SeedAccount("petar.petrovic@example.com", Role.USER),
            new SeedAccount("vojin.vojinovic@example.com", Role.USER),
            new SeedAccount("andjela.petrovic@example.com", Role.SUPPORT_AGENT),
            new SeedAccount("emily.wilson@example.com", Role.SUPPORT_AGENT),
            new SeedAccount("vesna.jovanovic@example.com", Role.USER),
            new SeedAccount("sarah.parker@example.com", Role.USER)
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "app.seed.password must be configured and nonblank when development seeding is enabled.");
        }
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    /**
     * Inserts missing development accounts in one transaction, leaving existing accounts unchanged.
     * New accounts are active by default.
     * Persistence and encoding failures propagate and roll back the transaction.
     */
    @Transactional
    public void seed() {
        for (SeedAccount account : ACCOUNTS) {
            if (!userRepository.existsByEmail(account.email())) {
                userRepository.save(new AppUser(
                        account.email(),
                        passwordEncoder.encode(password),
                        account.role()
                ));
            }
        }
    }

    private record SeedAccount(String email, Role role) {
    }
}
