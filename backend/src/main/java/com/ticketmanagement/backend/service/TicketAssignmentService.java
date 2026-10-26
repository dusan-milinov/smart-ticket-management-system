package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketAssignmentService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;


    public AppUser findLeastLoadedAgent() {
        List<AppUser> agents = userRepository.findByRoleAndActiveTrue(Role.SUPPORT_AGENT);
        return agents.stream()
                .min(Comparator.comparingInt(this::calculateWorkload)
                        .thenComparing(AppUser::getId))
                .orElseThrow(() -> new IllegalStateException("No active support agents available"));
    }


    private int calculateWorkload(AppUser agent) {

        List<Ticket> tickets = ticketRepository.findByAssigneeIdAndTicketStatusIn(
                agent.getId(),
                TicketStatus.UNRESOLVED
        );

        return tickets.stream()
                .mapToInt(ticket -> getPriority(ticket.getTicketPriority()))
                .sum();

    }

    private int getPriority(TicketPriority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 3;
            case HIGH -> 5;
        };
    }
}
