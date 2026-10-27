package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.model.AssignmentLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AssignmentLockRepository extends JpaRepository<AssignmentLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AssignmentLock a WHERE a.id = 1")
    Optional<AssignmentLock> lockAssignment();
}
