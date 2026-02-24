package com.turnero.api.service;

import com.turnero.api.model.Turno;
import com.turnero.api.repository.TurnoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TurnoServiceImpl implements TurnoService {

    private final TurnoRepository turnoRepository;

    public TurnoServiceImpl(TurnoRepository turnoRepository) {
        this.turnoRepository = turnoRepository;
    }

    @Override
    public void reservarTurno(Turno turno) {
        turnoRepository.save(turno);
    }

    @Override
    public List<Turno> listarTurnos() {
        return turnoRepository.findAll();
    }

    @Override
    public Turno buscarTurno(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
    }

    @Override
    public void updateTurno(Turno turno, Long id) {
        Turno existeTurno = buscarTurno(id);

        existeTurno.setClienteId(turno.getClienteId());
        existeTurno.setServicioId(turno.getServicioId());
        existeTurno.setProfesionalId(turno.getProfesionalId());
        existeTurno.setFechaHora(turno.getFechaHora());
        existeTurno.setDuracionMinutos(turno.getDuracionMinutos());
        existeTurno.setEstado(turno.getEstado());
        existeTurno.setObservaciones(turno.getObservaciones());

        turnoRepository.save(existeTurno);
        System.out.println("Turno con ID " + id + " actualizado exitosamente.");
    }

    @Override
    public void eliminarTurno(Long id) {
        if(turnoRepository.existsById(id)) {
            turnoRepository.deleteById(id);
            System.out.println("Turno con ID " + id + " eliminado exitosamente.");
        } else {
            throw new RuntimeException("Turno no encontrado");
        }
    }
}
