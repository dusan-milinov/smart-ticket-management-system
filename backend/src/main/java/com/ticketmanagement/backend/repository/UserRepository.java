package com.ticketmanagement.backend.repository;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    long countByRoleAndActiveTrue(Role role);

    Page<AppUser> findByIdNot(Long id, Pageable pageable);

    Page<AppUser> findByRole(Role role, Pageable pageable);

    /** Row lock serializing deactivation with concurrent assignment to the same account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(Long id);

    List<AppUser> findByRoleAndActiveTrue(Role role);
}
