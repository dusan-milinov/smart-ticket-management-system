package com.ticketmanagement.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.exception.IllegalStatusException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @Getter
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

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // TODO: add 'version' field for resolving conflicting updates by support agent and admin?

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private AppUser creator;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false)
    private TicketType ticketType;

    public void changeStatus(TicketStatus status) {
        this.ticketStatus = status;
    }

    public void assignTo(AppUser agent) {
        if(this.ticketStatus == TicketStatus.IN_QUEUE || this.ticketStatus == TicketStatus.FULFILLMENT) {
            throw new IllegalStatusException("Cannot assign a ticket that is already resolved or cancelled.");
        }
        changeStatus(TicketStatus.FULFILLMENT);
        this.assignee = agent;
    }

    public void resolve(String summary) {
        if(this.ticketStatus == TicketStatus.RESOLVED || this.ticketStatus == TicketStatus.CANCELLED) {
            throw new IllegalStatusException("Cannot resolve a ticket that is already resolved or cancelled.");
        }
        changeStatus(TicketStatus.RESOLVED);
        this.resolutionSummary = summary;
    }

    public void cancel() {
        if(this.ticketStatus == TicketStatus.RESOLVED || this.ticketStatus == TicketStatus.CANCELLED) {
            throw new IllegalStatusException("Cannot cancel a ticket that is already resolved or cancelled.");
        }
        changeStatus(TicketStatus.CANCELLED);
    }

}
