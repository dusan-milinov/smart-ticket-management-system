package com.ticketmanagement.backend.dto.comment;

import com.ticketmanagement.backend.model.Comment;

import java.time.Instant;

public record CommentResponse(
    Long id,
    String content,
    Instant createdAt,
    Long authorId,
    Long ticketId
){
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getAuthor().getId(),
                comment.getTicket().getTicketId()
        );
    }
}
