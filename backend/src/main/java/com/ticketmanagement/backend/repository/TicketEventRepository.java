package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.dto.ticket.ResolvedTicketDay;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEvent,Long> {

    List<TicketEvent> findAllByTicket(Ticket ticket);

    @Query("""
            SELECT new com.ticketmanagement.backend.dto.ticket.ResolvedTicketDay(
                CAST(e.createdAt AS LocalDate), COUNT(DISTINCT e.ticket.ticketId))
            FROM TicketEvent e
            WHERE e.initiator.id = :agentId
                AND e.eventType = com.ticketmanagement.backend.enums.TicketEventType.RESOLVED
                AND e.createdAt >= :start AND e.createdAt < :end
            GROUP BY CAST(e.createdAt AS LocalDate)
            ORDER BY CAST(e.createdAt AS LocalDate)
            """)
    List<ResolvedTicketDay> countDailyResolutions(
            @Param("agentId") Long agentId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
