package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket,Long>, JpaSpecificationExecutor<Ticket> {

    boolean existsByTicketNumber(String ticketNumber);

    long countByTicketStatus(TicketStatus status);

    long countByTicketPriorityAndTicketStatusIn(TicketPriority priority, Collection<TicketStatus> statuses);

    long countByAssigneeIsNullAndTicketStatusIn(Collection<TicketStatus> statuses);

    Optional<Ticket> findByTicketId(Long ticketId);

    Page<Ticket> findByTicketStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreatorId(Long creatorId, Pageable pageable);

    Page<Ticket> findByCreatorIdAndTicketStatus(Long creatorId, TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreatorIdOrAssigneeId(Long creatorId, Long assigneeId, Pageable pageable);

    Page<Ticket> findByAssigneeId(Long assigneeId, Pageable pageable);

    List<Ticket> findByAssigneeIdAndTicketStatus(Long assigneeId, TicketStatus status);
    List<Ticket> findByAssigneeIdAndTicketStatusIn(Long assigneeId, Collection<TicketStatus> statuses);

    long countByAssigneeIdAndTicketStatusIn(Long assigneeId, Collection<TicketStatus> statuses);
  
    Page<Ticket> findByAssigneeIdAndTicketStatus(Long assigneeId, TicketStatus status, Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.assignee.id = :assigneeId AND t.ticketStatus IN :statuses
            ORDER BY CASE t.ticketPriority
                WHEN com.ticketmanagement.backend.enums.TicketPriority.HIGH THEN 0
                WHEN com.ticketmanagement.backend.enums.TicketPriority.MEDIUM THEN 1
                WHEN com.ticketmanagement.backend.enums.TicketPriority.LOW THEN 2
                ELSE 3
            END ASC, t.createdAt ASC
            """)
    List<Ticket> findPriorityQueueByAssigneeIdAndTicketStatusIn(
            @Param("assigneeId") Long assigneeId,
            @Param("statuses") Collection<TicketStatus> statuses,
            Pageable pageable
    );
}
