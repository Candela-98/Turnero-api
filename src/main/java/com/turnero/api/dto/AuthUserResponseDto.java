package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.UserRole;

public record AuthUserResponseDto(
        Long id,
        String name,
        String email,
        UserRole role,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
