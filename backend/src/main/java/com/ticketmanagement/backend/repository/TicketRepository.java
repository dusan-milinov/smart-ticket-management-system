package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket,Long> {

    boolean existsByTicketNumber(String ticketNumber);

    List<Ticket> findAll();
    Optional<Ticket> findByTicketId(Long ticketId);

    void delete(Ticket ticket);

    List<Ticket> findByTicketStatus(TicketStatus status);

    List<Ticket> findByCreatorId(Long creatorId);

    List<Ticket> findByAssigneeId(Long assigneeId);
}
