package com.turnero.api.model;

import com.turnero.api.model.enums.UserRole;
import com.turnero.api.model.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    private String name;
    private String email;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    private String authSubject;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String avatarUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
