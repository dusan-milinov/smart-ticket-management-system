package com.ticketmanagement.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assignment_lock")
public class AssignmentLock {

    @Id
    private Long id;

    protected AssignmentLock(){

    }
}
