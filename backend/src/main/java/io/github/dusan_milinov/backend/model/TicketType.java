package io.github.dusan_milinov.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "ticket_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketType
{


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ticket type name cannot be empty")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @NotBlank(message = "Ticket type description cannot be empty")
    @Column(nullable = false, length = 255)
    private String description;

}
