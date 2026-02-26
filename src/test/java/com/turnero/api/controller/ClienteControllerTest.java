package com.turnero.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import com.turnero.api.dto.ClienteRequestDto;
import com.turnero.api.mapper.ClienteMapper;
import com.turnero.api.model.Cliente;
import com.turnero.api.service.ClienteService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClienteController.class)
class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteMapper clienteMapper;

    @MockitoBean
    private ClienteService clienteService;

    @Test
    void buscarCliente_devuelveCliente() throws Exception {

        Long id = 12L;

        ClienteRequestDto requestDto = new ClienteRequestDto();
        requestDto.setClienteId(id);
        requestDto.setNombreCliente("Juan");
        requestDto.setEmail("juan@mail.com");
        requestDto.setTelCliente("1122334455");
        requestDto.setFechaCreacion(LocalDateTime.of(2026,2,24,21,0));

        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre("Juan");
        cliente.setEmail("juan@mail.com");
        cliente.setTelefono("1122334455");
        cliente.setCreadoEn(LocalDateTime.of(2026, 2, 24, 21, 0));

        given(clienteMapper.toEntity(requestDto)).willReturn(cliente);
        given(clienteService.buscarCliente(id)).willReturn(cliente);

        mockMvc.perform(get("/api/clientes/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.telefono").value("1122334455"))
                .andExpect(jsonPath("$.creadoEn").value("2026-02-24T21:00:00"));
    }
}
