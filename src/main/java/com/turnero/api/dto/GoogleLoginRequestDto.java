package com.turnero.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDto(@NotBlank(message = "ID token is required") String idToken) {
}
