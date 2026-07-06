package com.turnero.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "staff_service_offerings")
public class StaffServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_member_id", nullable = false)
    private Long staffMemberId;

    @Column(name = "service_offering_id", nullable = false)
    private Long serviceOfferingId;
}
