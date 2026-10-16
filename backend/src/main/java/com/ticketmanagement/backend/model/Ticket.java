package com.ticketmanagement.backend.model;

import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @NotBlank(message = "Ticket title is required")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Ticket description is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false)
    private TicketStatus ticketStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_priority", nullable = false)
    private TicketPriority ticketPriority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // TODO: add 'version' field for resolving conflicting updates by support agent and admin?

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private AppUser creator;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne
    @JoinColumn(name = "ticket_type", nullable = false)
    private TicketType ticketType;

}
