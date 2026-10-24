package com.ticketmanagement.backend.config;

import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.seeder.UserSeeder;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.seeder.CommentSeeder;
import com.ticketmanagement.backend.seeder.TicketEventSeeder;
import com.ticketmanagement.backend.seeder.TicketFixtureSeeder;
import com.ticketmanagement.backend.seeder.TicketSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class SeedingConfig {

    @Bean
    public UserSeeder userSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.password:}") String password
    ) {
        return new UserSeeder(userRepository, passwordEncoder, password);
    }

    @Bean
    public TicketEventSeeder ticketEventSeeder(TicketEventRepository repository) {
        return new TicketEventSeeder(repository);
    }

    @Bean
    public CommentSeeder commentSeeder(CommentRepository repository, TicketEventSeeder eventSeeder) {
        return new CommentSeeder(repository, eventSeeder);
    }

    @Bean
    public TicketFixtureSeeder ticketFixtureSeeder(TicketRepository tickets, UserRepository users,
                                                   CommentSeeder comments, TicketEventSeeder events) {
        return new TicketFixtureSeeder(tickets, users, comments, events);
    }

    @Bean
    public TicketSeeder ticketSeeder(TicketFixtureSeeder fixtureSeeder) {
        return new TicketSeeder(fixtureSeeder);
    }

    @Bean
    public ApplicationRunner userSeedingRunner(UserSeeder userSeeder, TicketSeeder ticketSeeder) {
        return args -> {
            userSeeder.seed();
            ticketSeeder.validateTypes();
            ticketSeeder.seed();
        };
    }
}
