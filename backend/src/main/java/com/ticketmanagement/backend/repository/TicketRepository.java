package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.Ticket;
import jakarta.validation.constraints.Null;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket,Long> {

    boolean existsByTicketNumber(String ticketNumber);

    @NullMarked
    List<Ticket> findAll();
    Optional<Ticket> findByTicketId(Long ticketId);

    List<Ticket> findByTicketStatus(TicketStatus status);

    List<Ticket> findByCreatorId(Long creatorId);

    List<Ticket> findByCreatorIdAndTicketStatus(Long creatorId, TicketStatus status);

    List<Ticket> findByAssigneeId(Long assigneeId);

    long countByAssigneeIdAndTicketStatusIn(Long assigneeId, Collection<TicketStatus> statuses);
}
