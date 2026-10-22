package com.ticketmanagement.backend.model;

import com.ticketmanagement.backend.enums.TicketEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.Objects;

@Entity
@Data
@AllArgsConstructor
@Table(name = "ticket_events")
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TicketEventType eventType;

    //@NotBlank(message = "Must provide the old value.")
    // TODO: add length constraint
    @Column(name = "old_value", nullable = true)
    private String oldValue;

    //@NotBlank(message = "Must provide the new value.") ??????????????????????????????????????????????????????????????????????????????????????????????
    // TODO: add length constraint
    @Column(name = "new_value", nullable = true)
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private final Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private AppUser initiator;

    public TicketEvent() {
        this(Instant.now());
    }

    public TicketEvent(Instant createdAt) {
        this.createdAt = Objects.requireNonNull(createdAt, "Event timestamp is required");
    }

    public TicketEvent(Long id, TicketEventType eventType, String oldValue, String newValue,
                       Ticket ticket, AppUser initiator) {
        this();
        this.id = id;
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ticket = ticket;
        this.initiator = initiator;
    }
}
