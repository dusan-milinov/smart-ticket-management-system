package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RequiredArgsConstructor
public class TicketEventSeeder {

    private final TicketEventRepository ticketEventRepository;

    /** Adds fixture history only inside the owning ticket's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Ticket ticket, AppUser actor, TicketEventType type,
                       String oldValue, String newValue, Instant timestamp) {
        TicketEvent event = new TicketEvent(timestamp);
        event.setTicket(ticket);
        event.setInitiator(actor);
        event.setEventType(type);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        ticketEventRepository.save(event);
    }
}
