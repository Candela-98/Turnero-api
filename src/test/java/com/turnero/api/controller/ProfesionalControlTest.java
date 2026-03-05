package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ProfesionalRequestDto;
import com.turnero.api.dto.ServicioRequestDto;
import com.turnero.api.mapper.ProfesionalMapper;

import com.turnero.api.model.Profesional;
import com.turnero.api.model.Servicio;
import com.turnero.api.service.ProfesionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ProfesionalController.class)
public class ProfesionalControlTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProfesionalService pService;
    @MockitoBean
    private ProfesionalMapper pMapper;

    private ProfesionalRequestDto validDto(){
        ProfesionalRequestDto dto = new ProfesionalRequestDto();
        dto.setProfesionalId(1L);
        dto.setNombreProfesional("Daniel Leguizamon");
        dto.setEspecialidad("Barbero");
        dto.setMatricula("A12322");
        return dto;
    }

    private Profesional profesionalConId(long id){
        Profesional prof = new Profesional();
        prof.setId(id);
        prof.setNombre("Daniel Leguizamon");
        prof.setEspecialidad("Barbero");
        prof.setMatricula("A12322");
        return prof;
    }

    void altaProfesional_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception{
        ProfesionalRequestDto dto = validDto();
        Profesional entity = new Profesional();
        when(pMapper.toEntity(any(ProfesionalRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/profesionales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(pMapper).toEntity(any(ProfesionalRequestDto.class));
        verify(pService).altaProfesional(entity);
    }

    @Test
    void altaProfesiona_conDtoInvalido_deberiaRetornar400() throws Exception {
        ProfesionalRequestDto dto = validDto();
        dto.setNombreProfesional("");

        Profesional entity = new Profesional();
        when(pMapper.toEntity(any(ProfesionalRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/profesionales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(pMapper).toEntity(any(ProfesionalRequestDto.class));
        verify(pService).altaProfesional(entity);
    }

    @Test
    void listarProfesional_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception {
        when(pService.listarProfesional())
                .thenReturn(java.util.List.of(profesionalConId(1L), profesionalConId(2L)));

        mockMvc.perform(get("/api/profesionales"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(pService).listarProfesional();
    }

    @Test
    void buscarProfesional_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception{
        when (pService.buscarProfesional(1L)).thenReturn(profesionalConId(1L));

        mockMvc.perform(get("/api/profesionales/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Daniel Leguizamon"))
                .andExpect(jsonPath("$.especialidad").value("Barbero"))
                .andExpect(jsonPath("$.matricula").value("A12322"));
        verify(pService).buscarProfesional(1L);
    }

    @Test
    void buscarProfesional_conIdInexistente_deberiaRetornar404() throws Exception {
        when(pService.buscarProfesional(999L)).thenReturn(null);
        mockMvc.perform(get("/api/profesionales/999"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(pService).buscarProfesional(999L);
    }

    @Test
    void updateProfesional_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception{
        ProfesionalRequestDto dto = validDto();
        Profesional entity = new Profesional();
        when(pMapper.toEntity(any(ProfesionalRequestDto.class))).thenReturn(entity);
        mockMvc.perform(put("/api/profesionales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfesional_conDtoInvalido_deberiaRetornar400() throws Exception{
        ProfesionalRequestDto dto = validDto();
        dto.setNombreProfesional("");

        Profesional entity = new Profesional();
        when(pMapper.toEntity(any(ProfesionalRequestDto.class))).thenReturn(entity);

        mockMvc.perform(put("/api/profesionales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(pMapper).toEntity(any(ProfesionalRequestDto.class));
        verify(pService).updateProfesional(entity, 1L);
    }

    @Test
    void eliminarProfesional_ok_deberiaRetornar200_yLlamarAlServicio() throws Exception {
        mockMvc.perform(delete("/api/profesionales/1"))
                .andExpect(status().isOk());

        verify(pService).eliminarProfesional(1L);
    }
}

