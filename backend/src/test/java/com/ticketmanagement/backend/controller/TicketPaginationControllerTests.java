package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.config.SecurityConfig;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.security.JwtCookieService;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.TicketService;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, JwtCookieService.class})
class TicketPaginationControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private TicketService tickets;
    @MockitoBean private CurrentUserService currentUser;
    @MockitoBean private JwtDecoder decoder;

    @ParameterizedTest
    @ValueSource(strings = {"/tickets", "/tickets/status/IN_QUEUE", "/tickets/creator/11", "/tickets/assignee/12"})
    void usesBoundedAndDeterministicallySortedDefaults(String path) throws Exception {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("ticketId"));
        stub(path, pageable, Page.empty(pageable));

        mvc.perform(get(path).with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
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
