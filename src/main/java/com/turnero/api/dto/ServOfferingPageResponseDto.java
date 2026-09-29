package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ServOfferingPageResponseDto(List<ServOfferingResponseDto> data, PageInfo page) {
    public record PageInfo(int number, int size, @JsonProperty("total_elements") long totalElements,
                           @JsonProperty("total_pages") int totalPages) {}
}
