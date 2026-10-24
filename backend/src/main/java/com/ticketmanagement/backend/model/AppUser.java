package com.ticketmanagement.backend.model;

import com.ticketmanagement.backend.enums.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 30)
    private Role role;

    // The database default lets ddl-auto=update mark pre-existing rows active when adding this column.
    @ColumnDefault("true")
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean active = true;

    // Must match the JWT "token_version" claim; incrementing it invalidates every issued token.
    @ColumnDefault("0")
    @Column(name = "token_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private long tokenVersion = 0;

    public AppUser(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** Soft-deletes the account; the row and every reference to it are kept. */
    public void deactivate() {
        this.active = false;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

}
