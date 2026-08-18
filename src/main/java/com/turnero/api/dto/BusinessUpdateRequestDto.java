package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BusinessUpdateRequestDto {
    @Size(min = 1, max = 120) private String name;
    @Size(max = 120) private String industry;
    @Email @Size(max = 150) private String email;
    @Size(max = 50) private String phone;
    @Size(max = 255) private String address;
    @Size(min = 1, max = 80) private String timezone;
}
