package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.comment.CommentResponse;
import com.ticketmanagement.backend.dto.comment.CreateCommentRequest;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor

public class CommentService {
    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUserService currentUserService;
    private final TicketAccessPolicy ticketAccessPolicy;
    private final TicketEventRecorder ticketEventRecorder;

    @Transactional
    // @PreAuthorize("isAuthenticated()") instead?
    @PreAuthorize("hasAnyRole('USER', 'SUPPORT_AGENT', 'ADMIN')")
    public CommentResponse addComment(CreateCommentRequest comment) {
        AppUser author = currentUserService.getCurrentUser();
        Ticket ticket = ticketRepository.findByTicketId(comment.ticketId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + comment.ticketId() + " not found."));
        ticketAccessPolicy.requireRead(ticket, author);

        Comment newComment = new Comment();
        newComment.setContent(comment.content());
        newComment.setCreatedAt(Instant.now());
        newComment.setAuthor(author);
        newComment.setTicket(ticket);

        Comment savedComment = commentRepository.save(newComment);
        ticketEventRecorder.recordEvent(savedComment.getTicket().getTicketId(), author.getId(), TicketEventType.COMMENT_ADDED, null, null);
        return CommentResponse.from(savedComment);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'SUPPORT_AGENT', 'ADMIN')")
    public List<CommentResponse> getAllTicketComments(long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        ticketAccessPolicy.requireRead(ticket, currentUserService.getCurrentUser());
        return toResponses(commentRepository.findAllByTicket(ticket));
    }

    private List<CommentResponse> toResponses(List<Comment> comments) {
        return comments.stream().map(CommentResponse::from).toList();
    }
}
