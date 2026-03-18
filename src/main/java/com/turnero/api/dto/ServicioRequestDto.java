package com.turnero.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ServicioRequestDto {
    @NotNull(message = "El ID del servicio es obligatorio")
    private Long servicioId;

    @NotNull(message = "La duracion del servicio es obligatoria")
    @Min(value = 1, message = "La duración mínima es de 1 minuto")
    private int duracionMinutos;

    @NotNull(message = "El nombre del servicio es obligatorio")
    private String nombre;

    @NotNull(message = "El precio del servicio es obligatorio")
    @Positive(message = "El precio debe ser mayor a 0")
    private double precio;

    // Getters y setters

    public Long getServicioId() {
        return servicioId;
    }
    public void setServicioId(Long servicioId) {
        this.servicioId = servicioId;
    }

    public int getDuracionMinutos() {
        return duracionMinutos;
    }
    public void setDuracionMinutos(int duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
    }

    public String getNombre() {
        return nombre;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public double getPrecio() {
        return precio;
    }
    public void setPrecio(double precio) {
        this.precio = precio;
    }
}
