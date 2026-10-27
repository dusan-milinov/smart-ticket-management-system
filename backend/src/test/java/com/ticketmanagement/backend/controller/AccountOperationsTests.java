package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.security.JwtService;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, AdminController.class})
@Import({SecurityConfig.class, UserService.class, CurrentUserService.class, JwtCookieService.class})
class AccountOperationsTests {

    private static final String EMAIL = "owner@example.com";
    private static final String CURRENT = "Current-password-1";
    private static final String NEW = "Replacement-password-2";
    private static final String PASSWORD_REQUEST =
            "{\"currentPassword\":\"" + CURRENT + "\",\"newPassword\":\"" + NEW + "\"}";
    private static final String AGENT_REQUEST =
            "{\"email\":\"AGENT@example.com\",\"password\":\"" + NEW + "\"}";

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private UserRepository users;
    @MockitoBean private TicketRepository tickets;
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtDecoder decoder;

    private AppUser owner;
    private String originalHash;

    @BeforeEach
    void setUp() {
        originalHash = encoder.encode(CURRENT);
        owner = new AppUser(EMAIL, originalHash, Role.USER);
        owner.setId(41L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(owner));
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(99L);
            }
            return user;
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT_AGENT", "ADMIN"})
    void changesOnlyJwtSubjectPasswordForEveryRole(String role) throws Exception {
        mvc.perform(post("/auth/change-password").with(identity(role))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", containsString("access_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        assertThat(owner.getTokenVersion()).isEqualTo(1L);
        assertThat(encoder.matches(NEW, owner.getPassword())).isTrue();
        assertThat(encoder.matches(CURRENT, owner.getPassword())).isFalse();
        assertThat(owner.getPassword()).isNotEqualTo(NEW).isNotEqualTo(originalHash);
        assertThat(owner.getId()).isEqualTo(41L);
        assertThat(owner.getEmail()).isEqualTo(EMAIL);
        assertThat(owner.getRole()).isEqualTo(Role.USER);
        verify(users).findByEmail(EMAIL);
        verify(users).save(owner);
        verifyNoInteractions(jwtService);
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeAccount() throws Exception {
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-secret\",\"newPassword\":\"" + NEW + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        assertUnchanged();
    }

    @Test
    void unchangedPasswordDoesNotChangeAccount() throws Exception {
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + CURRENT + "\",\"newPassword\":\"" + CURRENT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must differ from the current password"));
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"currentPassword\":\"\",\"newPassword\":\"valid-password\"}",
            "{\"currentPassword\":\"password\",\"newPassword\":\"short\"}",
            "{\"currentPassword\":\"password\",\"newPassword\":\"        \"}"
    })
    void rejectsInvalidPasswordRequests(String json) throws Exception {
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(originalHash))));
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/change-password", "/admin/support-agents"})
    void protectedEndpointsRejectAnonymousRequests(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid JWT"));
        mvc.perform(post("/auth/change-password").header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cookie", "header"})
    void passwordChangeAuthenticatesThroughTheTokenResolver(String source) throws Exception {
        when(decoder.decode("valid-test-token")).thenReturn(rawToken("valid-test-token", 0L));
        var request = post("/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST);
        if (source.equals("cookie")) {
            request.cookie(new Cookie("access_token", "valid-test-token"));
        } else {
            request.header("Authorization", "Bearer valid-test-token");
        }

        mvc.perform(request).andExpect(status().isNoContent());

        verify(decoder).decode("valid-test-token");
        assertThat(encoder.matches(NEW, owner.getPassword())).isTrue();
        verify(users).save(owner);
    }

    @Test
    void passwordChangeRejectsAnExpiredCookie() throws Exception {
        when(decoder.decode("expired-test-token")).thenThrow(new BadJwtException("Expired JWT"));
        mvc.perform(post("/auth/change-password").cookie(new Cookie("access_token", "expired-test-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isUnauthorized());
        verify(decoder).decode("expired-test-token");
        verifyNoInteractions(users);
    }

    @Test
    void jwtForMissingAccountCannotChangePassword() throws Exception {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
    }

    @Test
    void adminCreatesEncodedAgentWithSafeResponse() throws Exception {
        mvc.perform(post("/admin/support-agents").with(identity("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.email").value("agent@example.com"))
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString(NEW))));
        var saved = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(encoder.matches(NEW, saved.getValue().getPassword())).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.SUPPORT_AGENT);
        verify(users).existsByEmail("agent@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT_AGENT"})
    void nonAdminCannotRegisterAgent(String role) throws Exception {
        mvc.perform(post("/admin/support-agents").with(identity(role))
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_REQUEST))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void duplicateAgentEmailReturnsConflict() throws Exception {
        when(users.existsByEmail("agent@example.com")).thenReturn(true);
        mvc.perform(post("/admin/support-agents").with(identity("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with email agent@example.com already exists."));
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"email\":\"invalid\",\"password\":\"valid-password\"}",
            "{\"email\":\"agent@example.com\",\"password\":\"short\"}",
            "{\"email\":\"agent@example.com\",\"password\":\"        \"}"
    })
    void rejectsInvalidAgentRequests(String json) throws Exception {
        mvc.perform(post("/admin/support-agents").with(identity("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verify(users, never()).save(any());
    }

    @Test
    void directAdminServiceCallNormalizesEmailAndForcesRole() {
        authenticate("ADMIN");
        var response = userService.registerSupportAgent(
                new RegisterSupportAgentRequest("Agent@Example.COM", NEW));
        assertThat(response.email()).isEqualTo("agent@example.com");
        assertThat(response.role()).isEqualTo("SUPPORT_AGENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT_AGENT"})
    void methodAuthorizationRejectsNonAdminWithoutHttp(String role) {
        authenticate(role);
        assertThatThrownBy(() -> userService.registerSupportAgent(
                new RegisterSupportAgentRequest("agent@example.com", NEW)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users);
    }

    @Test
    void methodAuthorizationRejectsUnauthenticatedCallsWithoutHttp() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> userService.changeOwnPassword(new ChangePasswordRequest(CURRENT, NEW)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> userService.registerSupportAgent(
                new RegisterSupportAgentRequest("agent@example.com", NEW)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void publicRegistrationStillCreatesOnlyRegularUsers() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(AGENT_REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminDeactivatesUserAndRepeatedRequestChangesNothing() throws Exception {
        AppUser admin = actingAdmin();
        AppUser target = account(7L, "target@example.com", Role.SUPPORT_AGENT);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(target));

        mvc.perform(patch("/admin/users/7/deactivate").with(identity("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());
        assertThat(target.isActive()).isFalse();
        verify(users).save(target);

        mvc.perform(patch("/admin/users/7/deactivate").with(identity("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(users).save(target);
        verify(tickets).countByAssigneeIdAndTicketStatusIn(7L, TicketStatus.UNRESOLVED);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void deactivatingUnknownUserReturnsNotFound() throws Exception {
        actingAdmin();
        when(users.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        mvc.perform(patch("/admin/users/404/deactivate").with(identity("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with ID 404 not found."));
        verify(users, never()).save(any());
    }

    @Test
    void administratorCannotDeactivateOwnAccount() throws Exception {
        AppUser admin = actingAdmin();
        when(users.findByIdForUpdate(41L)).thenReturn(Optional.of(admin));
        mvc.perform(patch("/admin/users/41/deactivate").with(identity("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Administrators cannot deactivate their own account"));
        assertThat(admin.isActive()).isTrue();
        verify(users, never()).save(any());
    }

    @Test
    void administratorCannotDeactivateAnotherAdministrator() throws Exception {
        actingAdmin();
        AppUser otherAdmin = account(8L, "other-admin@example.com", Role.ADMIN);
        when(users.findByIdForUpdate(8L)).thenReturn(Optional.of(otherAdmin));
        mvc.perform(patch("/admin/users/8/deactivate").with(identity("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Administrator accounts cannot be deactivated"));
        assertThat(otherAdmin.isActive()).isTrue();
        verify(users, never()).save(any());
    }

    @Test
    void agentWithUnresolvedTicketsCannotBeDeactivated() throws Exception {
        actingAdmin();
        AppUser agent = account(9L, "busy-agent@example.com", Role.SUPPORT_AGENT);
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(agent));
        when(tickets.countByAssigneeIdAndTicketStatusIn(9L, TicketStatus.UNRESOLVED)).thenReturn(2L);
        mvc.perform(patch("/admin/users/9/deactivate").with(identity("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "User has 2 unresolved assigned ticket(s); reassign them before deactivation"));
        assertThat(agent.isActive()).isTrue();
        verify(users, never()).save(any());
    }

    @Test
    void unresolvedTicketsAreQueuedOrInFulfillment() {
        assertThat(TicketStatus.UNRESOLVED)
                .containsExactlyInAnyOrder(TicketStatus.IN_QUEUE, TicketStatus.FULFILLMENT);
    }

    @Test
    void anonymousDeactivationIsRejected() throws Exception {
        mvc.perform(patch("/admin/users/7/deactivate")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT_AGENT"})
    void nonAdminCannotDeactivateUsers(String role) throws Exception {
        mvc.perform(patch("/admin/users/7/deactivate").with(identity(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPPORT_AGENT"})
    void deactivationServiceRejectsNonAdminWithoutHttp(String role) {
        authenticate(role);
        assertThatThrownBy(() -> userService.deactivateUser(7L)).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> userService.deactivateUser(7L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users, tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cookie", "header"})
    void unexpiredJwtOfInactiveAccountIsRejected(String source) throws Exception {
        owner.deactivate();
        when(decoder.decode("inactive-test-token")).thenReturn(rawToken("inactive-test-token", 0L));
        mvc.perform(withToken(post("/auth/change-password"), source, "inactive-test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isUnauthorized());
        assertUnchanged();
    }

    @Test
    void inactiveAccountIsRejectedEvenWhenTokenConversionIsBypassed() throws Exception {
        owner.deactivate();
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isUnauthorized());
        assertUnchanged();
    }

    @Test
    void jwtIssuedBeforePasswordChangeIsRejected() throws Exception {
        when(decoder.decode("old-test-token")).thenReturn(rawToken("old-test-token", 0L));
        when(decoder.decode("legacy-test-token")).thenReturn(rawToken("legacy-test-token", null));
        mvc.perform(withToken(post("/auth/change-password"), "cookie", "old-test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_REQUEST))
                .andExpect(status().isNoContent());
        assertThat(owner.getTokenVersion()).isEqualTo(1L);

        for (String token : List.of("old-test-token", "legacy-test-token")) {
            mvc.perform(withToken(post("/auth/change-password"), "header", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"" + NEW + "\",\"newPassword\":\"Third-password-3\"}"))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(encoder.matches(NEW, owner.getPassword())).isTrue();
        verify(users).save(owner);
    }

    @Test
    void publicLoginRemainsAccessible() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()));
        when(jwtService.generateToken(any())).thenReturn("test-response-token");
        mvc.perform(post("/auth/login").cookie(new Cookie("access_token", "stale-test-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(AGENT_REQUEST))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("access_token=test-response-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(content().string(""));
        verifyNoInteractions(decoder);
    }

    @Test
    void logoutClearsEvenAStaleCookieWithoutAuthentication() throws Exception {
        mvc.perform(post("/auth/logout").cookie(new Cookie("access_token", "stale-test-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(content().string(""));
        verifyNoInteractions(decoder, users);
    }

    @Test
    void suppliedAccountIdentifiersCannotOverrideJwtSubject() throws Exception {
        String json = PASSWORD_REQUEST.substring(0, PASSWORD_REQUEST.length() - 1)
                + ",\"userId\":999,\"email\":\"another@example.com\"}";
        mvc.perform(post("/auth/change-password").with(identity("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        verify(users).findByEmail(EMAIL);
        verify(users, never()).findByEmail("another@example.com");
        assertThat(encoder.matches(NEW, owner.getPassword())).isTrue();
    }

    @Test
    void suppliedAdminRoleCannotOverrideSupportAgentRole() throws Exception {
        String json = AGENT_REQUEST.substring(0, AGENT_REQUEST.length() - 1) + ",\"role\":\"ADMIN\"}";
        mvc.perform(post("/admin/support-agents").with(identity("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"));
    }

    @Test
    void serviceValidationRejectsInvalidAgentAndPasswordChangeRequests() {
        authenticate("ADMIN");
        assertThatThrownBy(() -> userService.registerSupportAgent(
                new RegisterSupportAgentRequest("invalid", "short")))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThatThrownBy(() -> userService.changeOwnPassword(new ChangePasswordRequest(CURRENT, "short")))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        verifyNoInteractions(users);
    }

    private RequestPostProcessor identity(String role) {
        return jwt().jwt(token -> token.subject(EMAIL))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private AppUser actingAdmin() {
        AppUser admin = account(41L, EMAIL, Role.ADMIN);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        return admin;
    }

    private AppUser account(long id, String email, Role role) {
        AppUser user = new AppUser(email, originalHash, role);
        user.setId(id);
        return user;
    }

    private Jwt rawToken(String value, Long tokenVersion) {
        var builder = Jwt.withTokenValue(value).header("alg", "HS256")
                .subject(EMAIL).claim("roles", List.of("ROLE_USER"));
        if (tokenVersion != null) {
            builder.claim("token_version", tokenVersion);
        }
        return builder.build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String source, String token) {
        return source.equals("cookie")
                ? request.cookie(new Cookie("access_token", token))
                : request.header("Authorization", "Bearer " + token);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void assertUnchanged() {
        assertThat(owner.getPassword()).isEqualTo(originalHash);
        verify(users, never()).save(any());
    }
}
