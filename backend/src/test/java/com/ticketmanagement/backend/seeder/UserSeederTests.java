package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSeederTests {

    private static final String PASSWORD = "test-only-seed-password";
    private static final Map<String, Role> EXPECTED_ACCOUNTS = Map.of(
            "admin@example.com", Role.ADMIN,
            "zika@example.com", Role.SUPPORT_AGENT,
            "nikola@example.com", Role.SUPPORT_AGENT,
            "agent3@example.com", Role.SUPPORT_AGENT,
            "petar.petrovic@example.com", Role.USER,
            "vojin.vojinovic@example.com", Role.USER
    );

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = spy(new SecurityConfig().passwordEncoder());
    private final Map<String, AppUser> accounts = new LinkedHashMap<>();
    private UserSeeder seeder;

    @BeforeEach
    void setUp() {
        when(userRepository.existsByEmail(anyString()))
                .thenAnswer(invocation -> accounts.containsKey(invocation.getArgument(0)));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            assertThat(accounts).doesNotContainKey(user.getEmail());
            accounts.put(user.getEmail(), user);
            return user;
        });
        seeder = new UserSeeder(userRepository, passwordEncoder, PASSWORD);
    }

    @Test
    void seedsExpectedAccountsWithEncodedPasswordsAndUnassignedIds() {
        seeder.seed();

        assertThat(accounts).containsOnlyKeys(EXPECTED_ACCOUNTS.keySet());
        EXPECTED_ACCOUNTS.forEach((email, role) -> {
            AppUser user = accounts.get(email);
            assertThat(user.getRole()).isEqualTo(role);
            assertThat(user.getId()).isNull();
            assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
            assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
            verify(userRepository).existsByEmail(email);
        });
        verify(userRepository, times(6)).save(any(AppUser.class));
        verify(userRepository, never()).count();
    }

    @Test
    void repeatedRunsDoNotInsertOrReencodeExistingAccounts() {
        seeder.seed();
        Map<String, AppUser> originalAccounts = new LinkedHashMap<>(accounts);
        Map<String, String> originalPasswords = new LinkedHashMap<>();
        accounts.forEach((email, user) -> originalPasswords.put(email, user.getPassword()));
        clearInvocations(userRepository, passwordEncoder);

        new UserSeeder(userRepository, passwordEncoder, "changed-test-password").seed();

        assertThat(accounts).isEqualTo(originalAccounts);
        accounts.forEach((email, user) ->
                assertThat(user.getPassword()).isEqualTo(originalPasswords.get(email)));
        EXPECTED_ACCOUNTS.keySet().forEach(email -> verify(userRepository).existsByEmail(email));
        verify(userRepository, never()).save(any(AppUser.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void fillsPartialDataWithoutChangingExistingPasswordsRolesOrIds() {
        AppUser existingAdmin = new AppUser("admin@example.com", "existing-admin-hash", Role.USER);
        existingAdmin.setId(42L);
        AppUser existingAgent = new AppUser("nikola@example.com", "existing-agent-hash", Role.ADMIN);
        existingAgent.setId(99L);
        accounts.put(existingAdmin.getEmail(), existingAdmin);
        accounts.put(existingAgent.getEmail(), existingAgent);

        seeder.seed();

        assertThat(accounts).hasSize(6);
        assertThat(accounts.get(existingAdmin.getEmail())).isSameAs(existingAdmin);
        assertThat(existingAdmin.getId()).isEqualTo(42L);
        assertThat(existingAdmin.getPassword()).isEqualTo("existing-admin-hash");
        assertThat(existingAdmin.getRole()).isEqualTo(Role.USER);
        assertThat(accounts.get(existingAgent.getEmail())).isSameAs(existingAgent);
        assertThat(existingAgent.getId()).isEqualTo(99L);
        assertThat(existingAgent.getPassword()).isEqualTo("existing-agent-hash");
        assertThat(existingAgent.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, times(4)).save(any(AppUser.class));
        verify(passwordEncoder, times(4)).encode(PASSWORD);
    }

    @Test
    void rejectsMissingOrBlankPasswordWithoutRepositoryAccess() {
        for (String password : new String[]{null, "", " \t\n"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new UserSeeder(userRepository, passwordEncoder, password))
                    .withMessage("app.seed.password must be configured and nonblank when development seeding is enabled.");
        }
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void propagatesEncodingFailureWithoutSavingAccount() {
        IllegalStateException failure = new IllegalStateException("encoding failed");
        doThrow(failure).when(passwordEncoder).encode(PASSWORD);

        assertThatThrownBy(seeder::seed).isSameAs(failure);

        verify(userRepository, never()).save(any(AppUser.class));
    }
}
