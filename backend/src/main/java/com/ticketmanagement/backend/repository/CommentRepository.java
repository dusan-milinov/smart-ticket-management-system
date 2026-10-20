package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment,Long> {
    List<Comment> findAllByTicket(Ticket ticket);
}
