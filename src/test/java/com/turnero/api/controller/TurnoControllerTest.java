package com.turnero.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.controller.TurnoController;
import com.turnero.api.dto.TurnoRequestDto;
import com.turnero.api.mapper.TurnoMapper;
import com.turnero.api.model.EstadoTurno;
import com.turnero.api.model.Turno;
import com.turnero.api.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TurnoController.class)
public class TurnoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TurnoService turnoService;
    @MockitoBean
    private TurnoMapper turnoMapper;

    private TurnoRequestDto validDto() {
        TurnoRequestDto dto = new TurnoRequestDto();
        dto.setClienteId(1L);
        dto.setServicioId(2L);
        dto.setProfesionalId(3L);
        dto.setFechaHora(LocalDateTime.now().plusDays(1)); // cumple @Future
        dto.setDuracionMinutos(30); // cumple @Min(1)
        dto.setEstado(EstadoTurno.CONFIRMADO);
        dto.setObservaciones("Obs");
        return dto;
    }

    private Turno turnoConId(long id) {
        Turno t = new Turno();
        t.setId(id);
        t.setClienteId(1L);
        t.setServicioId(2L);
        t.setProfesionalId(3L);
        t.setFechaHora(LocalDateTime.of(2026, 2, 15, 10, 0));
        t.setDuracionMinutos(30);
        t.setEstado(EstadoTurno.CONFIRMADO);
        t.setObservaciones("Obs");
        t.setCreadoEn(LocalDateTime.of(2026, 2, 1, 10, 0));
        t.setActualizadoEn(LocalDateTime.of(2026, 2, 2, 10, 0));
        return t;
    }

    @Test
    void reservarTurno_ok_deberiaRetornar200_yLlamarService() throws Exception {
        TurnoRequestDto dto = validDto();
        Turno entity = new Turno();

        when(turnoMapper.toEntity(any(TurnoRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/turnos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(turnoMapper).toEntity(any(TurnoRequestDto.class));
        verify(turnoService).reservarTurno(entity);
    }

    @Test
    void reservarTurno_conDtoInvalido_deberiaRetornar400() throws Exception{
        TurnoRequestDto dto = validDto();
        dto.setClienteId(null); // invalida por @NotNull

        mockMvc.perform(post("/api/turnos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(turnoService, never()).reservarTurno(any());
        verify(turnoMapper, never()).toEntity(any());
    }

    @Test
    void listarTurnos_ok_deberiaRetornarListaTurnos() throws Exception{
        when(turnoService.listarTurnos()).thenReturn(List.of(turnoConId(1L), turnoConId(2L)));

        mockMvc.perform(get("/api/turnos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(turnoService).listarTurnos();

    }

    @Test
    void buscarTurno_ok_deberiaRetornarTurno() throws Exception {
        when(turnoService.buscarTurno(10L)).thenReturn(turnoConId(10));

        mockMvc.perform(get("/api/turnos/10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.clienteId").value(1))
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        verify(turnoService).buscarTurno(10L);
    }

    @Test
    void updateTurno_ok_deberiaRetornar200_yLlamarService() throws Exception{
        TurnoRequestDto dto = validDto();
        Turno entity = new Turno();

        when(turnoMapper.toEntity(any(TurnoRequestDto.class))).thenReturn(entity);
            mockMvc.perform(put("/api/turnos/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
            verify(turnoMapper).toEntity(any(TurnoRequestDto.class));
            verify(turnoService).updateTurno(entity, 5L);

    }

    @Test
    void updateTurno_conDtoInvalido_deberiaRetornar400() throws Exception{
        TurnoRequestDto dto = validDto();
        dto.setFechaHora(LocalDateTime.now().minusDays(1)); //rompe @Feature

        mockMvc.perform(put("/api/turnos/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
        verify(turnoService, never()).updateTurno(any(), anyLong());
        verify(turnoMapper, never()).toEntity(any());
    }

    @Test
    void eliminarTurno_ok_deberiaRetornar200_yLlamarService() throws Exception{
        mockMvc.perform(delete("/api/turnos/7"))
                .andExpect(status().isOk());
        verify(turnoService).eliminarTurno(7L);
    }

}
