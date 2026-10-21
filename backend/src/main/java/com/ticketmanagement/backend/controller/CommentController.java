package com.ticketmanagement.backend.controller;


import com.ticketmanagement.backend.dto.comment.CommentResponse;
import com.ticketmanagement.backend.dto.comment.CreateCommentRequest;
import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
import com.ticketmanagement.backend.service.CommentService;
import com.ticketmanagement.backend.service.TicketEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/{ticketId}")
    public ResponseEntity<List<CommentResponse>> getAllTicketComments(@PathVariable long ticketId) {
        return ResponseEntity.ok(commentService.getAllTicketComments(ticketId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(@RequestBody CreateCommentRequest commentRequest) {
        // TODO: Ovde iz jwt token-a treba da se izvuče ID korisnika koji pravi komentar, i taj ID se prosleđuje kao drugi argument metodi addComment.
        return ResponseEntity.ok(commentService.addComment(commentRequest, 1));
    }

}
