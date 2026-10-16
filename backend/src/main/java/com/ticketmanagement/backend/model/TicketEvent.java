package com.ticketmanagement.backend.model;

import com.ticketmanagement.backend.enums.TicketEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
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

    @NotBlank(message = "Must provide the old value.")
    // TODO: add length constraint
    @Column(name = "old_value", nullable = false)
    private String oldValue;

    @NotBlank(message = "Must provide the new value.")
    // TODO: add length constraint
    @Column(name = "new_value", nullable = false)
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private final Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private AppUser initiator;
}
