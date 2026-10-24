package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket,Long> {

    boolean existsByTicketNumber(String ticketNumber);

    Optional<Ticket> findByTicketId(Long ticketId);

    Page<Ticket> findByTicketStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreatorId(Long creatorId, Pageable pageable);

    Page<Ticket> findByCreatorIdAndTicketStatus(Long creatorId, TicketStatus status, Pageable pageable);

    Page<Ticket> findByAssigneeId(Long assigneeId, Pageable pageable);

    Page<Ticket> findByAssigneeIdAndTicketStatus(Long assigneeId, TicketStatus status, Pageable pageable);
}
