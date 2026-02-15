package com.turnero.api.service;

import com.turnero.api.model.Profesional;
import com.turnero.api.model.Servicio;
import com.turnero.api.model.Turno;

import java.util.List;

public interface ServicioService {

    Servicio altaServicio(Servicio servicio);

    List<Servicio> listarServicio();

    Servicio buscarServicio(Long id);

    void updateServicio(Servicio servicio, Long id);

    void eliminarServicio(Long id);
}
