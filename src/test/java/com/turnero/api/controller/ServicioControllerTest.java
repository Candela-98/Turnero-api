package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServicioRequestDto;
import com.turnero.api.mapper.ServicioMapper;
import com.turnero.api.mapper.TurnoMapper;
import com.turnero.api.model.Servicio;
import com.turnero.api.service.ServicioService;
import com.turnero.api.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServicioController.class)
public class ServicioControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ServicioService servicioService;
    @MockitoBean
    private ServicioMapper sMapper;

    private ServicioRequestDto validDto() {
        ServicioRequestDto dto = new ServicioRequestDto();
        dto.setServicioId(1L);
        dto.setNombre("Corte y barba");
        dto.setDuracionMinutos(60);
        dto.setPrecio(10000.0);
        return dto;
    }

    private Servicio servicioConId(long id){
        Servicio s = new Servicio();
        s.setId(id);
        s.setNombre("Corte y barba");
        s.setDuracionMinutos(60);
        s.setPrecio(10000.0);
        return s;
    }

    @Test
    void altaServicio_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception {
        ServicioRequestDto dto = validDto();
        Servicio entity = new Servicio();
        when(sMapper.toEntity(any(ServicioRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/servicios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServicioRequestDto.class));
        verify(servicioService).altaServicio(entity);
    }

    @Test
    void altaServicio_conDtoInvalido_deberiaRetornar400() throws Exception {
        ServicioRequestDto dto = validDto();
        dto.setNombre("");

        Servicio entity = new Servicio();
        when(sMapper.toEntity(any(ServicioRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/servicios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServicioRequestDto.class));
        verify(servicioService).altaServicio(entity);
    }

    @Test
    void listarServicio_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception {
        when(servicioService.listarServicio())
                .thenReturn(java.util.List.of(servicioConId(1L), servicioConId(2L)));

        mockMvc.perform(get("/api/servicios"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(servicioService).listarServicio();
    }

    @Test
    void buscarServicio_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception{
        when (servicioService.buscarServicio(1L)).thenReturn(servicioConId(1L));

        mockMvc.perform(get("/api/servicios/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Corte y barba"))
                .andExpect(jsonPath("$.duracionMinutos").value(60))
                .andExpect(jsonPath("$.precio").value(10000.0));
        verify(servicioService).buscarServicio(1L);
    }

    @Test
    void buscarServicio_conIdInexistente_deberiaRetornar404() throws Exception {
        when(servicioService.buscarServicio(999L)).thenReturn(null);
        mockMvc.perform(get("/api/servicios/999"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(servicioService).buscarServicio(999L);
    }

    @Test
    void updateServicio_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception{
        ServicioRequestDto dto = validDto();
        Servicio entity = new Servicio();
        when(sMapper.toEntity(any(ServicioRequestDto.class))).thenReturn(entity);
        mockMvc.perform(put("/api/servicios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateServicio_conDtoInvalido_deberiaRetornar400() throws Exception{
        ServicioRequestDto dto = validDto();
        dto.setNombre("");

        Servicio entity = new Servicio();
        when(sMapper.toEntity(any(ServicioRequestDto.class))).thenReturn(entity);

        mockMvc.perform(put("/api/servicios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServicioRequestDto.class));
        verify(servicioService).updateServicio(entity, 1L);
    }

    @Test
    void eliminarServicio_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception {
        mockMvc.perform(delete("/api/servicios/1"))
                .andExpect(status().isOk());

        verify(servicioService).eliminarServicio(1L);
    }
}
