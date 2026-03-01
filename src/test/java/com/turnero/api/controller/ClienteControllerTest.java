package com.turnero.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClienteMapper clienteMapper;

    @MockitoBean
    private ClienteService clienteService;

    @Test
    void retrieveClient_whenClientExists_returnsClientData() throws Exception {
        // Given
        Long id = 12L;

        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre("Juan");
        cliente.setEmail("juan@mail.com");
        cliente.setTelefono("1122334455");
        cliente.setCreadoEn(LocalDateTime.of(2026, 2, 24, 21, 0));

        given(clienteService.buscarCliente(id)).willReturn(cliente);

        // When + Assert
        mockMvc.perform(get("/api/clientes/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.telefono").value("1122334455"))
                .andExpect(jsonPath("$.creadoEn").value("2026-02-24T21:00:00"));

        then(clienteService).should().buscarCliente(any());
    }

    @Test
    void retrieveClient_whenIdIsInvalid_returnsBadRequest() throws Exception {
        // Given
        var id = "invalidId";

        // When
        mockMvc.perform(get("/api/clientes/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Assert
        then(clienteService).should(never()).buscarCliente(any());
    }

    @Test
    void altaCliente_whenRequestIsValid_returns200() throws Exception {
        // Given
        Long id = 12L;
        ClienteRequestDto dto = getClientDTO(id);

        Cliente entity = new Cliente();
        entity.setId(12L);
        entity.setNombre("Juan Perez");
        entity.setEmail("juan@mail.com");
        entity.setTelefono("1122334455");
        entity.setCreadoEn(LocalDateTime.of(2026, 2, 24, 21, 0));

        given(clienteMapper.toEntity(any(ClienteRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Assert
        then(clienteMapper).should().toEntity(any(ClienteRequestDto.class));
        then(clienteService).should().altaCliente(entity);
    }

    private ClienteRequestDto getClientDTO(Long id) {
        ClienteRequestDto requestDto = new ClienteRequestDto();
        requestDto.setClienteId(id);
        requestDto.setNombreCliente("Juan");
        requestDto.setEmail("juan@mail.com");
        requestDto.setTelCliente("1122334455");
        requestDto.setFechaCreacion(LocalDateTime.of(2026, 2, 24, 21, 0));

        return requestDto;
    }

}
