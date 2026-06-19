package com.turnero.api.exception;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {

    @Schema(description = "The HTTP status code of the error", example = "400")
    private int status;

    @Schema(description = "HTTP error type", example = "Bad Request")
    private String error;

    @Schema(description = "Application error code", example = "VALIDATION_ERROR")
    private String code;

    @Schema(description = "Detailed error message", example = "Appointment not found with ID: 1")
    private String message;

    @Schema(description = "Additional error details")
    private List<ErrorDetail> details;

    @Schema(description = "Request path", example = "/api/v1/appointments")
    private String path;

    @Schema(description = "The timestamp when the error occurred", example = "2024-12-01T10:00:00")
    private LocalDateTime timestamp;
}
