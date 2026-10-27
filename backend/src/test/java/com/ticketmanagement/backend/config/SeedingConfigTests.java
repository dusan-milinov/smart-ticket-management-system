package com.ticketmanagement.backend.config;

import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.seeder.CommentSeeder;
import com.ticketmanagement.backend.seeder.TicketEventSeeder;
import com.ticketmanagement.backend.seeder.TicketFixtureSeeder;
import com.ticketmanagement.backend.seeder.TicketSeeder;
import com.ticketmanagement.backend.seeder.UserSeeder;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeedingConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SeedingConfig.class, TestDependencies.class);

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
                        .doesNotHaveBean(TicketSeeder.class)
                        .doesNotHaveBean(TicketFixtureSeeder.class)
                        .doesNotHaveBean(CommentSeeder.class)
                        .doesNotHaveBean(TicketEventSeeder.class)
                        .doesNotHaveBean(ApplicationRunner.class);
                verifyNoInteractions(context.getBean(UserRepository.class),
                        context.getBean(PasswordEncoder.class), context.getBean(TicketRepository.class),
                        context.getBean(CommentRepository.class), context.getBean(TicketEventRepository.class));
            });
        }
    }

    @Test
    void enabledRunnerUsesSeparateUserAndTicketTransactions() {
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
            order.verify(repository).existsByEmail("andjela.petrovic@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("emily.wilson@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("vesna.jovanovic@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(repository).existsByEmail("sarah.parker@example.com");
            order.verify(repository).save(any(AppUser.class));
            order.verify(transactionManager).commit(any());
            verify(transactionManager, times(29)).getTransaction(any());
            verify(transactionManager, times(29)).commit(any());
            verify(transactionManager, never()).rollback(any());
        });
    }

    @Test
    void runnerSeedsUsersThenValidatesTypesThenSeedsTickets() throws Exception {
        UserSeeder users = mock(UserSeeder.class);
        TicketSeeder tickets = mock(TicketSeeder.class);

        new SeedingConfig().userSeedingRunner(users, tickets).run(new DefaultApplicationArguments());

        var order = inOrder(users, tickets);
        order.verify(users).seed();
        order.verify(tickets).validateTypes();
        order.verify(tickets).seed();
        order.verifyNoMoreInteractions();
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
                                .hasRootCauseMessage("app.seed.password must be configured and nonblank when development seeding is enabled.");
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
            when(repository.save(argThat(user -> user.getEmail().equals("sarah.parker@example.com"))))
                    .thenThrow(failure);

            assertThatThrownBy(() -> context.getBean(ApplicationRunner.class)
                    .run(new DefaultApplicationArguments())).isSameAs(failure);

            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
            verify(transactionManager).getTransaction(any());
            verify(repository, times(10)).save(any(AppUser.class));
            verifyNoInteractions(context.getBean(TicketRepository.class));
        });
    }

    @Test
    void fixtureFailureRollsBackOnlyItsTransactionAndStopsTheRun() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=dev",
                "app.seed.enabled=true",
                "app.seed.password=test-only-password"
        ).run(context -> {
            TicketRepository repository = context.getBean(TicketRepository.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            IllegalStateException failure = new IllegalStateException("fixture lookup failed");
            when(repository.existsByTicketNumber("TKT-SEED-0010")).thenThrow(failure);

            assertThatThrownBy(() -> context.getBean(ApplicationRunner.class)
                    .run(new DefaultApplicationArguments())).isSameAs(failure);

            // One user transaction and nine completed fixture transactions precede the failure.
            verify(transactionManager, times(11)).getTransaction(any());
            verify(transactionManager, times(10)).commit(any());
            verify(transactionManager).rollback(any());
            verify(repository, times(10)).existsByTicketNumber(anyString());
            verify(repository, never()).existsByTicketNumber("TKT-SEED-0011");
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
        TicketRepository ticketRepository() {
            TicketRepository repository = mock(TicketRepository.class);
            when(repository.existsByTicketNumber(anyString())).thenReturn(true);
            return repository;
        }

        @Bean
        CommentRepository commentRepository() {
            return mock(CommentRepository.class);
        }

        @Bean
        TicketEventRepository ticketEventRepository() {
            return mock(TicketEventRepository.class);
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
