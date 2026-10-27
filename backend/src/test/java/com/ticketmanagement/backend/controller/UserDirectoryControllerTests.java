package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.security.JwtService;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, AdminController.class})
@Import({SecurityConfig.class, UserService.class, CurrentUserService.class, JwtCookieService.class})
class UserDirectoryControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private TicketRepository tickets;
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void supportAgentGetsPaginatedNonAdminUsersWithActiveStatus() throws Exception {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.asc("id")));
        AppUser user = account(8L, "user@example.com", Role.USER, true);
        AppUser agent = account(9L, "inactive-agent@example.com", Role.SUPPORT_AGENT, false);
        when(users.findByRoleNot(Role.ADMIN, pageable))
                .thenReturn(new PageImpl<>(List.of(user, agent), pageable, 3));

        mvc.perform(get("/users").param("page", "1").param("size", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(8))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[1].email").value("inactive-agent@example.com"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));

        verify(users).findByRoleNot(Role.ADMIN, pageable);
    }

    @Test
    void administratorGetsAllAccountsIncludingInactiveAccounts() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("id")));
        when(users.findAll(pageable)).thenReturn(new PageImpl<>(
                List.of(account(1L, "admin@example.com", Role.ADMIN, true),
                        account(2L, "inactive@example.com", Role.USER, false)),
                pageable, 2));

        mvc.perform(get("/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[1].active").value(false))
                .andExpect(jsonPath("$.content[1].password").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(users).findAll(pageable);
    }

    @Test
    void administratorGetsOnlySupportAgentsIncludingInactiveAgents() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("id")));
        when(users.findByRole(Role.SUPPORT_AGENT, pageable)).thenReturn(new PageImpl<>(
                List.of(account(3L, "agent@example.com", Role.SUPPORT_AGENT, false)), pageable, 1));

        mvc.perform(get("/admin/support-agents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.content[0].active").value(false));

        verify(users).findByRole(Role.SUPPORT_AGENT, pageable);
    }

    @Test
    void regularUserCannotListAccounts() throws Exception {
        mvc.perform(get("/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void supportAgentCannotAccessAdministratorDirectories() throws Exception {
        var supportAgent = jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"));
        mvc.perform(get("/admin/users").with(supportAgent)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/support-agents").with(supportAgent)).andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void anonymousRequestsCannotListAccounts() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());

        verifyNoInteractions(users);
    }

    @Test
    void administratorViewsAnyAccountAndItsCreatedAndAssignedTickets() throws Exception {
        AppUser admin = account(1L, "admin@example.com", Role.ADMIN, true);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt")));
        when(users.findByEmail("admin@example.com")).thenReturn(java.util.Optional.of(admin));
        when(users.findById(2L)).thenReturn(java.util.Optional.of(account(2L, "u@example.com", Role.USER, false)));
        when(tickets.findByCreatorIdOrAssigneeId(2L, 2L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var auth = jwt().jwt(j -> j.subject("admin@example.com")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/users/2").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("u@example.com"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/users/2/tickets").with(auth)).andExpect(status().isOk());

        verify(tickets).findByCreatorIdOrAssigneeId(2L, 2L, pageable);
    }

    @Test
    void supportAgentSeesOnlyTicketsAssignedToThemselves() throws Exception {
        AppUser agent = account(5L, "agent@example.com", Role.SUPPORT_AGENT, true);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt")));
        when(users.findByEmail("agent@example.com")).thenReturn(java.util.Optional.of(agent));
        when(users.findById(2L)).thenReturn(java.util.Optional.of(account(2L, "u@example.com", Role.USER, true)));
        when(tickets.findByCreatorIdAndAssigneeId(2L, 5L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/users/2/tickets").with(jwt().jwt(j -> j.subject("agent@example.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"))))
                .andExpect(status().isOk());

        verify(tickets).findByCreatorIdAndAssigneeId(2L, 5L, pageable);
    }

    @Test
    void supportAgentCannotViewAdministratorDetails() throws Exception {
        AppUser agent = account(5L, "agent@example.com", Role.SUPPORT_AGENT, true);
        when(users.findByEmail("agent@example.com")).thenReturn(java.util.Optional.of(agent));
        when(users.findById(1L)).thenReturn(java.util.Optional.of(account(1L, "admin@example.com", Role.ADMIN, true)));

        var auth = jwt().jwt(j -> j.subject("agent@example.com")).authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"));
        mvc.perform(get("/users/1").with(auth)).andExpect(status().isNotFound());
        mvc.perform(get("/users/1/tickets").with(auth)).andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotViewUserDetails() throws Exception {
        var auth = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(get("/users/2").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get("/users/2/tickets").with(auth)).andExpect(status().isForbidden());
    }

    private AppUser account(long id, String email, Role role, boolean active) {
        AppUser user = new AppUser(email, "encoded-password", role);
        user.setId(id);
        if (!active) {
            user.deactivate();
        }
        return user;
    }
}

