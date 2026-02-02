package com.turnero.api.service;

import com.turnero.api.model.Turno;
import com.turnero.api.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TurnoServiceImplTest {

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private TurnoServiceImpl turnoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

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
}

