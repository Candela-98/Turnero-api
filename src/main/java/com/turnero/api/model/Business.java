package com.turnero.api.model;

import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
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
@Table(name = "businesses")
public class Business {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String slug;
    private String industry;
    private String email;
    private String phone;
    private String address;
    private String timezone;

    @Enumerated(EnumType.STRING)
    private BusinessStatus status;

    @Enumerated(EnumType.STRING)
    private BusinessOnboardingStatus onboardingStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
