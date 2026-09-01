package com.turnero.api.dto;

public record GoogleIdentityDto(String subject, String email, boolean emailVerified) {
}
