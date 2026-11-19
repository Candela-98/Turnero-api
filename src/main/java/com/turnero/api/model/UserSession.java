package com.turnero.api.model;

import com.turnero.api.model.enums.SessionScope;
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
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long customerId;

    private String sessionTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SessionScope scope;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;

    private String ipAddress;
    private String userAgent;
}
