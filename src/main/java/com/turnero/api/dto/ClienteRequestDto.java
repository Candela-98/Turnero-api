package com.turnero.api.dto;

import com.turnero.api.model.EstadoTurno;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class ClienteRequestDto {

    @NotNull(message = "El ID del cliente es obligatorio")
    private Long clienteId;

    @NotNull(message = "El nombre del cliente es obligatorio")
    private String nombreCliente;

    @NotNull(message = "El email del cliente es obligatorio")
    private String email;

    @NotNull(message = "El telefono del cliente es obligatorio")
    private String telCliente;

    @NotNull(message = "La fecha de creacion del cliente es obligatoria")
    private LocalDateTime fechaCreacion;

    //Getters y Setters

    public Long getClienteId() {
        return clienteId;
    }
    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }
    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelCliente() {
        return telCliente;
    }
    public void setTelCliente(String telCliente) {
        this.telCliente = telCliente;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
