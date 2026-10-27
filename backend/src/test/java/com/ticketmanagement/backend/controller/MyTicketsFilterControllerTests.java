package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, JwtCookieService.class})
class MyTicketsFilterControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private TicketService tickets;
    @MockitoBean private CurrentUserService currentUser;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private UserRepository users;

    @BeforeEach
    void signIn() {
        AppUser user = new AppUser("user@example.com", "password", Role.USER);
        user.setId(11L);
        when(currentUser.getCurrentUser()).thenReturn(user);
    }

    @Test
    void myTicketsForwardFiltersAndKeepNewestFirstPaging() throws Exception {
        Pageable pageable = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "ticketId"));
        when(tickets.searchTicketsByCreator(11L, TicketStatus.RESOLVED, TicketPriority.HIGH, "vpn", pageable))
                .thenReturn(Page.empty(pageable));

        mvc.perform(get("/tickets/my").with(jwt().authorities(() -> "ROLE_USER"))
                        .param("status", "RESOLVED").param("priority", "HIGH").param("search", "vpn")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2));

        verify(tickets).searchTicketsByCreator(11L, TicketStatus.RESOLVED, TicketPriority.HIGH, "vpn", pageable);
    }

    @Test
    void myTicketsFiltersAreOptional() throws Exception {
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "ticketId"));
        when(tickets.searchTicketsByCreator(11L, null, null, null, pageable)).thenReturn(Page.empty(pageable));

        mvc.perform(get("/tickets/my").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());

        verify(tickets).searchTicketsByCreator(11L, null, null, null, pageable);
    }

    @Test
    void myTicketsRejectUnknownStatuses() throws Exception {
        mvc.perform(get("/tickets/my").with(jwt().authorities(() -> "ROLE_USER")).param("status", "OPEN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tickets);
    }

    @Test
    void statusCountsUseTheCurrentUserAndOptionalFilters() throws Exception {
        when(tickets.countTicketsByCreator(11L, TicketPriority.LOW, "vpn"))
                .thenReturn(Map.of(TicketStatus.IN_QUEUE, 2L));

        mvc.perform(get("/tickets/my/status-counts").with(jwt().authorities(() -> "ROLE_USER"))
                        .param("priority", "LOW").param("search", "vpn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IN_QUEUE").value(2));
    }
}
