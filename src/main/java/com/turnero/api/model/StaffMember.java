package com.turnero.api.model;

import com.turnero.api.model.enums.StaffMemberStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "staff_members")
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    private Long userId;

    private String name;

    private String roleLabel;

    private String specialty;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private StaffMemberStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
