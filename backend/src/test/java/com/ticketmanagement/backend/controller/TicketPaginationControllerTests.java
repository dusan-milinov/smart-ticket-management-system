package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.TicketService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, JwtCookieService.class})
class TicketPaginationControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private TicketService tickets;
    @MockitoBean private CurrentUserService currentUser;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private UserRepository users;

    @Test
    void assignedTicketsUseTheCurrentAgentAndNewestFirstDefaults() throws Exception {
        AppUser agent = new AppUser("agent@example.com", "password", Role.SUPPORT_AGENT);
        agent.setId(12L);
        when(currentUser.getCurrentUser()).thenReturn(agent);
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "ticketId"));
        when(tickets.getTicketsByAssigneeId(12L, pageable)).thenReturn(Page.empty(pageable));

        mvc.perform(get("/tickets/assigned").with(jwt().authorities(() -> "ROLE_SUPPORT_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.size").value(5));

        verify(tickets).getTicketsByAssigneeId(12L, pageable);
        verifyNoMoreInteractions(tickets);
    }

    @Test
    void assignedTicketsForwardPagingAndReturnTicketContent() throws Exception {
        AppUser agent = new AppUser("agent@example.com", "password", Role.SUPPORT_AGENT);
        agent.setId(12L);
        when(currentUser.getCurrentUser()).thenReturn(agent);
        Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "ticketId"));
        TicketResponse ticket = new TicketResponse(42L, "TKT-TEST", "Title", "Description",
                TicketStatus.FULFILLMENT, null, null, null, null, 11L, 12L, null);
        when(tickets.getTicketsByAssigneeId(12L, pageable))
                .thenReturn(new PageImpl<>(List.of(ticket), pageable, 11));

        mvc.perform(get("/tickets/assigned").with(jwt().authorities(() -> "ROLE_SUPPORT_AGENT"))
                        .param("page", "1").param("size", "10").param("assigneeId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(42))
                .andExpect(jsonPath("$.content[0].assigneeId").value(12))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(tickets).getTicketsByAssigneeId(12L, pageable);
        verifyNoMoreInteractions(tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void assignedTicketsRejectOtherRoles(String role) throws Exception {
        mvc.perform(get("/tickets/assigned").with(jwt().authorities(() -> role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tickets, currentUser);
    }

    @Test
    void assignedTicketsRequireAuthentication() throws Exception {
        mvc.perform(get("/tickets/assigned"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tickets, currentUser);
    }

    @Test
    void startingTicketProcessingUsesCurrentAgent() throws Exception {
        AppUser agent = new AppUser("agent@example.com", "password", Role.SUPPORT_AGENT);
        agent.setId(12L);
        when(currentUser.getCurrentUser()).thenReturn(agent);
        TicketResponse ticket = new TicketResponse(42L, "TKT-TEST", "Title", "Description",
                TicketStatus.FULFILLMENT, null, null, null, null, 11L, 12L, null);
        when(tickets.startTicketProcessing(42L, 12L)).thenReturn(ticket);

        mvc.perform(patch("/tickets/42/start").with(jwt().authorities(() -> "ROLE_SUPPORT_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(42))
                .andExpect(jsonPath("$.ticketStatus").value("FULFILLMENT"));

        verify(tickets).startTicketProcessing(42L, 12L);
        verifyNoMoreInteractions(tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void startingTicketProcessingRejectsOtherRoles(String role) throws Exception {
        mvc.perform(patch("/tickets/42/start").with(jwt().authorities(() -> role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tickets, currentUser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tickets", "/tickets/status/IN_QUEUE", "/tickets/creator/11", "/tickets/assignee/12"})
    void usesBoundedAndDeterministicallySortedDefaults(String path) throws Exception {
        Pageable pageable = PageRequest.of(0, 5, Sort.by("ticketId"));
        stub(path, pageable, Page.empty(pageable));

        mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verifyRequest(path, pageable);
        verifyNoInteractions(currentUser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tickets", "/tickets/status/IN_QUEUE", "/tickets/creator/11", "/tickets/assignee/12"})
    void forwardsRequestedPageSizeAndSortAndReturnsContentWithTotals(String path) throws Exception {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("ticketId")));
        TicketResponse ticket = new TicketResponse(42L, "TKT-TEST", "Title", "Description",
                TicketStatus.IN_QUEUE, null, null, null, null, 11L, null, null);
        stub(path, pageable, new PageImpl<>(List.of(ticket), pageable, 3));

        mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_USER"))
                        .param("page", "1").param("size", "2")
                        .param("sort", "createdAt,desc", "ticketId,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].ticketId").value(42))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));

        verifyRequest(path, pageable);
    }

    private void verifyRequest(String path, Pageable pageable) {
        switch (path) {
            case "/tickets" -> verify(tickets).getAllTickets(pageable);
            case "/tickets/status/IN_QUEUE" -> verify(tickets).getTicketsByStatus(TicketStatus.IN_QUEUE, pageable);
            case "/tickets/creator/11" -> verify(tickets).getTicketsByUserId(11L, pageable);
            case "/tickets/assignee/12" -> verify(tickets).getTicketsByAssigneeId(12L, pageable);
            default -> throw new IllegalArgumentException("Unexpected path: " + path);
        }
        verifyNoMoreInteractions(tickets);
    }

    private void stub(String path, Pageable pageable, Page<TicketResponse> page) {
        switch (path) {
            case "/tickets" -> when(tickets.getAllTickets(pageable)).thenReturn(page);
            case "/tickets/status/IN_QUEUE" ->
                    when(tickets.getTicketsByStatus(TicketStatus.IN_QUEUE, pageable)).thenReturn(page);
            case "/tickets/creator/11" -> when(tickets.getTicketsByUserId(11L, pageable)).thenReturn(page);
            case "/tickets/assignee/12" -> when(tickets.getTicketsByAssigneeId(12L, pageable)).thenReturn(page);
            default -> throw new IllegalArgumentException("Unexpected path: " + path);
        }
    }
}
