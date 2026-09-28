package com.turnero.api.controller;

import com.turnero.api.dto.PublicCancellationRequestDto;
import com.turnero.api.dto.PublicCancellationResponseDto;
import com.turnero.api.dto.PublicCancellationResultResponseDto;
import com.turnero.api.service.PublicCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/cancellations")
@RequiredArgsConstructor
public class PublicCancellationController {

    private final PublicCancellationService publicCancellationService;

    @GetMapping("/{token}")
    public ResponseEntity<PublicCancellationResponseDto> getPublicCancellation(@PathVariable String token) {

        PublicCancellationResponseDto response = publicCancellationService.getPublicCancellation(token);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{token}")
    public ResponseEntity<PublicCancellationResultResponseDto> cancelPublicAppointment(@PathVariable String token, @RequestBody PublicCancellationRequestDto request) {

        PublicCancellationResultResponseDto response = publicCancellationService.cancelPublicAppointment(token, request);

        return ResponseEntity.ok(response);
    }
}
