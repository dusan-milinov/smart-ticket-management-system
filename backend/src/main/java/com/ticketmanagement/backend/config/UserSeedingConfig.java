package com.ticketmanagement.backend.config;

import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.service.UserSeeder;
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
public class UserSeedingConfig {

    @Bean
    public UserSeeder userSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.password:}") String password
    ) {
        return new UserSeeder(userRepository, passwordEncoder, password);
    }

    @Bean
    public ApplicationRunner userSeedingRunner(UserSeeder userSeeder) {
        return args -> userSeeder.seed();
    }
}
