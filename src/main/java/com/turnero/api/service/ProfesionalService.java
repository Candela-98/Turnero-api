package com.turnero.api.service;

import com.turnero.api.model.Cliente;
import com.turnero.api.model.Profesional;
import com.turnero.api.model.Turno;

import java.util.List;

public interface ProfesionalService {

    Profesional altaProfesional(Profesional profesional);

    Profesional buscarProfesional(Long id);

    List<Profesional> listarProfesional();

    void updateProfesional(Profesional profesional, Long id);

    void eliminarProfesional(Long id);
}
