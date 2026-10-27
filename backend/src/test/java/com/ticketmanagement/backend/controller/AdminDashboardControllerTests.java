package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, AdminDashboardService.class, JwtCookieService.class})
class AdminDashboardControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private TicketRepository tickets;
    @MockitoBean private UserRepository users;
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void adminReceivesSystemTotalsIncludingZeroBuckets() throws Exception {
        var open = List.of(TicketStatus.IN_QUEUE, TicketStatus.FULFILLMENT);
        when(tickets.countByTicketStatus(TicketStatus.FULFILLMENT)).thenReturn(14L);
        when(tickets.countByTicketStatus(TicketStatus.RESOLVED)).thenReturn(10L);
        when(tickets.countByTicketStatus(TicketStatus.CANCELLED)).thenReturn(4L);
        when(tickets.countByTicketPriorityAndTicketStatusIn(TicketPriority.HIGH, open)).thenReturn(5L);
        when(tickets.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(users.count()).thenReturn(10L);
        when(users.countByRole(Role.USER)).thenReturn(4L);
        when(users.countByRole(Role.SUPPORT_AGENT)).thenReturn(5L);
        when(users.countByRoleAndActiveTrue(Role.SUPPORT_AGENT)).thenReturn(3L);

        mvc.perform(get("/admin/dashboard").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(28))
                .andExpect(jsonPath("$.ticketsByStatus.IN_QUEUE").value(0))
                .andExpect(jsonPath("$.ticketsByStatus.FULFILLMENT").value(14))
                .andExpect(jsonPath("$.openTicketsByPriority.HIGH").value(5))
                .andExpect(jsonPath("$.openTicketsByPriority.LOW").value(0))
                .andExpect(jsonPath("$.unassignedOpenTickets").value(0))
                .andExpect(jsonPath("$.totalAccounts").value(10))
                .andExpect(jsonPath("$.regularUsers").value(4))
                .andExpect(jsonPath("$.supportAgents").value(5))
                .andExpect(jsonPath("$.activeSupportAgents").value(3))
                .andExpect(jsonPath("$.recentTickets").isEmpty());
    }

    @Test
    void anonymousRequestsCannotReadStatistics() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_SUPPORT_AGENT"})
    void nonAdministratorsCannotReadStatistics(String role) throws Exception {
        mvc.perform(get("/admin/dashboard").with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tickets, users);
    }
}
