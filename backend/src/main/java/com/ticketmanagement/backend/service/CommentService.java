package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.comment.CommentResponse;
import com.ticketmanagement.backend.dto.comment.CreateCommentRequest;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor

public class CommentService {
    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketEventService ticketEventService;

    @Transactional
    public CommentResponse addComment(CreateCommentRequest comment, long actorId) {
        AppUser author = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + actorId + " not found."));
        Ticket ticket = ticketRepository.findByTicketId(comment.ticketId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + comment.ticketId() + " not found."));

        Comment newComment = new Comment();
        newComment.setContent(comment.content());
        newComment.setCreatedAt(Instant.now());
        newComment.setAuthor(author);
        newComment.setTicket(ticket);

        Comment savedComment = commentRepository.save(newComment);
        ticketEventService.recordEvent(savedComment.getTicket().getTicketId(), actorId, TicketEventType.COMMENT_ADDED, null, null);
        return CommentResponse.from(savedComment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getAllTicketComments(long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        return toResponses(commentRepository.findAllByTicket(ticket));
    }

    private List<CommentResponse> toResponses(List<Comment> comments) {
        return comments.stream().map(CommentResponse::from).toList();
    }
}
