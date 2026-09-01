package com.turnero.api.dto;

import com.turnero.api.model.enums.UserRole;

public record AuthMeResponseDto(Long userId,
                                String name,
                                String email,
                                UserRole role,
                                Long businessId,
                                String businessName,
                                String businessSlug) {
}
