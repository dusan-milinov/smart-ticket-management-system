package com.ticketmanagement.backend.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotBlank(message = "Comment content is required")
        String content,
        @NotNull(message = "Ticket ID is required")
        Long ticketId
) {
}
