package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RequiredArgsConstructor
public class CommentSeeder {

    private final CommentRepository commentRepository;
    private final TicketEventSeeder eventSeeder;

    /** Persists a fixture comment and its history in the owning ticket's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(Ticket ticket, AppUser author, String content, Instant timestamp) {
        boolean creator = author.getId().equals(ticket.getCreator().getId());
        boolean assignedAgent = author.getRole() == Role.SUPPORT_AGENT
                && author.getId().equals(ticket.getAssignee().getId());
        if (!creator && !assignedAgent && author.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Invalid seed comment author for " + ticket.getTicketNumber()
                    + ": " + author.getEmail());
        }
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(timestamp);
        commentRepository.save(comment);
        ticket.setUpdatedAt(timestamp);
        eventSeeder.record(ticket, author, TicketEventType.COMMENT_ADDED, null, null, timestamp);
    }
}
