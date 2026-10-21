package com.ticketmanagement.backend.config;

import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.service.UserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSeedingConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UserSeedingConfig.class, TestDependencies.class);

    @Test
    void disablesSeedingWithoutBothGuardsAndDoesNotRequirePassword() {
        String[][] disabledConfigurations = {
                {},
                {"spring.profiles.active=dev"},
                {"app.seed.enabled=true"},
                {"spring.profiles.active=prod", "app.seed.enabled=true"},
                {"spring.profiles.active=dev", "app.seed.enabled=false"}
        };

        for (String[] properties : disabledConfigurations) {
            contextRunner.withPropertyValues(properties).run(context -> {
                assertThat(context).hasNotFailed()
                        .doesNotHaveBean(UserSeeder.class)
                        .doesNotHaveBean(ApplicationRunner.class);
                verifyNoInteractions(context.getBean(UserRepository.class),
                        context.getBean(PasswordEncoder.class));
            });
        }
    }

    @Test
    void enabledRunnerDelegatesToSeederInsideOneTransaction() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=dev",
                "app.seed.enabled=true",
                "app.seed.password=test-only-password"
        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(UserSeeder.class)
                    .hasSingleBean(ApplicationRunner.class);
            UserRepository repository = context.getBean(UserRepository.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            verifyNoInteractions(repository);

            context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());

            var order = inOrder(transactionManager, repository);
            order.verify(transactionManager).getTransaction(any());
            order.verify(repository).existsByEmail("admin@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("zika@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("nikola@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("agent3@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("petar.petrovic@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("vojin.vojinovic@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(transactionManager).commit(any());
            verify(transactionManager, times(1)).getTransaction(any());
            verify(transactionManager, never()).rollback(any());
        });
    }

    @Test
    void enabledSeedingRequiresNonblankPassword() {
        for (String password : new String[]{"", "app.seed.password=", "app.seed.password=   "}) {
            contextRunner.withPropertyValues(
                    "spring.profiles.active=dev", "app.seed.enabled=true"
            ).withPropertyValues(password.isEmpty() ? new String[]{} : new String[]{password})
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                                .hasRootCauseMessage("app.seed.password must be configured and nonblank when development seeding is enabled");
                    });
        }
    }

    @Test
    void persistenceFailureRollsBackTheSeedTransaction() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=dev",
                "app.seed.enabled=true",
                "app.seed.password=test-only-password"
        ).run(context -> {
            UserRepository repository = context.getBean(UserRepository.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            IllegalStateException failure = new IllegalStateException("database write failed");
            when(repository.save(any(AppUser.class))).thenThrow(failure);

            assertThatThrownBy(() -> context.getBean(ApplicationRunner.class)
                    .run(new DefaultApplicationArguments())).isSameAs(failure);

            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        });
    }

    @TestConfiguration
    @EnableTransactionManagement
    static class TestDependencies {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            when(encoder.encode(anyString())).thenReturn("test-encoded-password");
            return encoder;
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
            return manager;
        }
    }
}
