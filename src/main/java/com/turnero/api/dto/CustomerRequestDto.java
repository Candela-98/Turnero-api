package com.turnero.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class CustomerRequestDto {

    @NotNull(message = "The customer ID is mandatory.")
    private Long customerId;

    @NotNull(message = "The customer's name is required.")
    private String nameCustomer;

    @NotNull(message = "The customer's email address is required.")
    private String email;

    @NotNull(message = "The customer's phone number is mandatory.")
    private String phoneCustomer;

    @NotNull(message = "The customer creation date is mandatory.")
    private LocalDateTime creationDate;

    //Getters y Setters


    public Long getCustomerId() {
        return customerId;
    }
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getNameCustomer() {
        return nameCustomer;
    }
    public void setNameCustomer(String nameCustomer) {
        this.nameCustomer = nameCustomer;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneCustomer() {
        return phoneCustomer;
    }
    public void setPhoneCustomer(String phoneCustomer) {
        this.phoneCustomer = phoneCustomer;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
}
