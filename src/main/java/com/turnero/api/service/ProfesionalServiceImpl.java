package com.turnero.api.service;

import com.turnero.api.model.Cliente;
import com.turnero.api.model.Profesional;
import com.turnero.api.model.Turno;
import com.turnero.api.repository.ProfesionalRepository;

import java.util.List;

public class ProfesionalServiceImpl implements ProfesionalService{

    private final ProfesionalRepository profesionalRepository;

    public ProfesionalServiceImpl(ProfesionalRepository profesionalRepository) {
        this.profesionalRepository = profesionalRepository;
    }

    @Override
    public Profesional altaProfesional(Profesional profesional) {
        return profesionalRepository.save(profesional);
    }

    @Override
    public Profesional buscarProfesional(Long id) {
        return profesionalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Profesional no encontrado"));
    }

    @Override
    public void updateProfesional(Profesional profesional, Long id) {
        Profesional profesionalExiste = buscarProfesional(id);

        profesionalExiste.setNombre(profesional.getNombre());
        profesionalExiste.setEspecialidad(profesional.getEspecialidad());
        profesionalExiste.setMatricula(profesional.getMatricula());

        profesionalRepository.save(profesionalExiste);
        System.out.println("Profesional con ID " + id + " actualizado exitosamente.");
    }

    public List<Profesional> listarProfesional() {
        return profesionalRepository.findAll();
    }

    @Override
    public void eliminarProfesional(Long id) {
        if(profesionalRepository.existsById(id)) {
            profesionalRepository.deleteById(id);
            System.out.println("Profesional con ID " + id + " eliminado exitosamente.");
        } else {
            throw new RuntimeException("Profesional no encontrado");
        }

    }
}
