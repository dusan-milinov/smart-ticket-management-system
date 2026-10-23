package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.exception.UserAlreadyExistsException;
import com.ticketmanagement.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@Transactional
class AccountPersistenceTests {

    @Autowired private UserService service;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtAuthenticationConverter authenticationConverter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordChangePersistsAndChangesLoginWithoutRevokingIssuedToken() {
        String email = uniqueEmail();
        service.register(new RegisterRequest(email, "Original-password-1"));
        String token = service.login(new LoginRequest(email, "Original-password-1")).token();
        SecurityContextHolder.getContext().setAuthentication(authenticationConverter.convert(decoder.decode(token)));

        service.changeOwnPassword(new ChangePasswordRequest("Original-password-1", "Replacement-password-2"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> service.login(new LoginRequest(email, "Original-password-1")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(service.login(new LoginRequest(email, "Replacement-password-2")).token()).isNotBlank();
        assertThat(decoder.decode(token).getSubject()).isEqualTo(email);
        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void agentRegistrationPersistsNormalizedEmailAndPreventsDuplicateAccount() {
        String email = uniqueEmail();
        var response = service.registerSupportAgent(
                new RegisterSupportAgentRequest(email.toUpperCase(java.util.Locale.ROOT), "Agent-password-1"));
        entityManager.flush();
        entityManager.clear();

        var stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getId()).isEqualTo(response.id());
        assertThat(stored.getRole()).isEqualTo(Role.SUPPORT_AGENT);
        assertThat(service.login(new LoginRequest(email, "Agent-password-1")).token()).isNotBlank();
        assertThatThrownBy(() -> service.registerSupportAgent(
                new RegisterSupportAgentRequest(email, "Different-password-2")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    private String uniqueEmail() {
        return "account-test-" + UUID.randomUUID() + "@example.com";
    }
}
