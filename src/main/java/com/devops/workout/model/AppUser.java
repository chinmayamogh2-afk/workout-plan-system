package com.devops.workout.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

/**
 * Application user entity.
 * Roles: TRAINER (creates/edits plans), ADMIN (approves/publishes).
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Roles stored as a comma-separated string for simplicity.
     * Values: ROLE_TRAINER, ROLE_ADMIN
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles;
}
