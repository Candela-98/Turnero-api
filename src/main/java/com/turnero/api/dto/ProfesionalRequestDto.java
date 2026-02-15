package com.turnero.api.dto;

import jakarta.validation.constraints.NotNull;

public class ProfesionalRequestDto {
    @NotNull(message = "El ID del profesional es obligatorio")
    private Long profesionalId;

    @NotNull(message = "El nombre del profesional es obligatorio")
    private String nombreProfesional;

    @NotNull(message = "La especialidad del profesional es obligatoria")
    private String especialidad;

    @NotNull(message = "La matricula del proefsional es obligatoria")
    private String matricula;

    //Getters y Setters

    public Long getProfesionalId() {
        return profesionalId;
    }
    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public String getNombreProfesional() {
        return nombreProfesional;
    }
    public void setNombreProfesional(String nombreProfesional) {
        this.nombreProfesional = nombreProfesional;
    }

    public String getEspecialidad() {
        return especialidad;
    }
    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public String getMatricula() {
        return matricula;
    }
    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }
}
