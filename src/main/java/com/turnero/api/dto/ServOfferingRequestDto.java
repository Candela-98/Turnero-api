package com.turnero.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ServOfferingRequestDto {
    @NotNull(message = "The service offering ID is required")
    private Long Id;

    @NotNull(message = "The service offering duration is required")
    @Min(value = 1, message = "The duration minimum is 1 minute")
    private int durationMinutes;

    @NotNull(message = "The service offering name is required")
    private String name;

    @NotNull(message = "The service offering price is required")
    @Positive(message = "The price must be greater than 0")
    private double price;

    // Getters y setters

    public Long getId() {
        return Id;
    }
    public void setId(Long id) {
        this.Id = id;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
}
