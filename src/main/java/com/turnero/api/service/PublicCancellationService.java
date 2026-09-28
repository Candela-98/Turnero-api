package com.turnero.api.service;

import com.turnero.api.dto.PublicCancellationRequestDto;
import com.turnero.api.dto.PublicCancellationResponseDto;
import com.turnero.api.dto.PublicCancellationResultResponseDto;

public interface PublicCancellationService {
    PublicCancellationResponseDto getPublicCancellation(String token);

    PublicCancellationResultResponseDto cancelPublicAppointment(
            String token,
            PublicCancellationRequestDto request
    );
}
