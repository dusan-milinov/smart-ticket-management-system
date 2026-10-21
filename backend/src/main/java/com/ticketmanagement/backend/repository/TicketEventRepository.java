package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEvent,Long> {

    List<TicketEvent> findAllByTicket(Ticket ticket);
}
