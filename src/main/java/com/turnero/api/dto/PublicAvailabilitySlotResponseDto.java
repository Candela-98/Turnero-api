package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicAvailabilitySlotResponseDto {

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private List<Long> availableStaffMemberIds;

}
