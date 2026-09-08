package com.turnero.api.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleLoginRequestDto(
        @JsonProperty("id_token") @NotBlank(message = "ID token is required") String idToken
) {
}
