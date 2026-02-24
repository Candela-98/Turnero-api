package com.turnero.api.service;

import com.turnero.api.model.Turno;
import com.turnero.api.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoServiceImplTest {

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private TurnoServiceImpl turnoService;

    @Test
    void reservarTurno_deberiaGuardarTurno() {
        Turno turno = new Turno();
        turnoService.reservarTurno(turno);
        verify(turnoRepository, times(1)).save(turno);
    }

    @Test
    void listarTurnos_deberiaRetornarListaDeTurnos() {
        Turno turno1 = new Turno();
        Turno turno2 = new Turno();
        when(turnoRepository.findAll()).thenReturn(Arrays.asList(turno1, turno2));
        List<Turno> turnos = turnoService.listarTurnos();
        assertEquals(2, turnos.size());
        assertTrue(turnos.contains(turno1));
        assertTrue(turnos.contains(turno2));
    }

    @Test
    void reservarTurno_cuandoRepositoryFalla_lanzaExcepcion() {
        Turno turno = new Turno();
        doThrow(new RuntimeException("Error al guardar")).when(turnoRepository).save(turno);
        assertThrows(RuntimeException.class, () -> turnoService.reservarTurno(turno));
    }

    @Test
    void buscarTurno_cuandoExiste_retornaTurno() {
        Long id = 1L;
        Turno turno = new Turno();
        when(turnoRepository.findById(id)).thenReturn(Optional.of(turno));

        Turno resultado = turnoService.buscarTurno(id);

        assertNotNull(resultado);
        assertEquals(turno, resultado);
        verify(turnoRepository, times(1)).findById(id);
    }

    @Test
    void buscarTurno_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(turnoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> turnoService.buscarTurno(id));
    }

    @Test
    void updateTurno_deberiaActualizarYGuardar() {
        Long id = 1L;

        Turno existente = new Turno();
        existente.setId(id);

        Turno cambios = new Turno();
        cambios.setClienteId(10L);
        cambios.setServicioId(20L);
        cambios.setProfesionalId(30L);
        cambios.setFechaHora(java.time.LocalDateTime.of(2026, 2, 15, 10, 0));
        cambios.setDuracionMinutos(45);
        cambios.setEstado(com.turnero.api.model.EstadoTurno.CONFIRMADO);
        cambios.setObservaciones("Obs");

        when(turnoRepository.findById(id)).thenReturn(Optional.of(existente));

        turnoService.updateTurno(cambios, id);

        verify(turnoRepository, times(1)).save(existente);

        assertEquals(10L, existente.getClienteId());
        assertEquals(20L, existente.getServicioId());
        assertEquals(30L, existente.getProfesionalId());
        assertEquals(java.time.LocalDateTime.of(2026, 2, 15, 10, 0), existente.getFechaHora());
        assertEquals(45, existente.getDuracionMinutos());
        assertEquals(com.turnero.api.model.EstadoTurno.CONFIRMADO, existente.getEstado());
        assertEquals("Obs", existente.getObservaciones());
    }

    @Test
    void eliminarTurno_cuandoExiste_deberiaEliminar() {
        Long id = 1L;
        when(turnoRepository.existsById(id)).thenReturn(true);

        turnoService.eliminarTurno(id);

        verify(turnoRepository, times(1)).deleteById(id);
    }

    @Test
    void eliminarTurno_cuandoNoExiste_noElimina() {
        Long id = 99L;
        when(turnoRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> turnoService.eliminarTurno(id));

        verify(turnoRepository, never()).deleteById(anyLong());
    }
}

