package com.turnero.api.service;

import com.turnero.api.model.Servicio;
import com.turnero.api.model.Turno;
import com.turnero.api.repository.ServicioRepository;

import java.util.List;

public class ServicioServiceImpl implements ServicioService{
    private final ServicioRepository servicioRepository;

    public ServicioServiceImpl(ServicioRepository servicioRepository) {
        this.servicioRepository = servicioRepository;
    }

    @Override
    public Servicio altaServicio(Servicio servicio) {
        return servicioRepository.save(servicio);
    }

    @Override
    public Servicio buscarServicio(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
    }

    public List<Servicio> listarServicio() {
        return servicioRepository.findAll();
    }

    @Override
    public void updateServicio(Servicio servicio, Long id) {
        Servicio servicioExiste = buscarServicio(id);

        servicioExiste.setNombre(servicio.getNombre());
        servicioExiste.setDuracionMinutos(servicio.getDuracionMinutos());
        servicioExiste.setPrecio(servicio.getPrecio());

        servicioRepository.save(servicioExiste);
        System.out.println("Servicio con ID " + id + " actualizado exitosamente.");

    }

    @Override
    public void eliminarServicio(Long id) {
        if(servicioRepository.existsById(id)){
            servicioRepository.deleteById(id);
            System.out.println("El servicio con Id: " + id + " se elimino correctamente.");
        }

    }
}
