package com.ticketmanagement.backend.controller;


import com.ticketmanagement.backend.dto.comment.CommentResponse;
import com.ticketmanagement.backend.dto.comment.CreateCommentRequest;
import com.ticketmanagement.backend.service.CommentService;
import jakarta.validation.Valid;
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
    public ResponseEntity<CommentResponse> addComment(@Valid @RequestBody CreateCommentRequest commentRequest) {
        return ResponseEntity.ok(commentService.addComment(commentRequest));
    }

}
