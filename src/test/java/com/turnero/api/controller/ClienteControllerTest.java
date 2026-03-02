package com.turnero.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

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

    @Test
    void altaCliente_whenNombreIsNull_returns400() throws Exception {
        // Given:
        Long id = 12L;
        ClienteRequestDto dto = getClientDTO(id);
        dto.setNombreCliente(null);

        // When + Then
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        then(clienteService).shouldHaveNoInteractions();
    }

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
    void updateCliente_whenRequestIsValid_returns200() throws Exception {
        // Given
        Long id = 12L;
        ClienteRequestDto dto = getClientDTO(id);
        Cliente entity = getClientEntity(id);

        given(clienteMapper.toEntity(any(ClienteRequestDto.class)))
                .willReturn(entity);

        // When + Then
        mockMvc.perform(put("/api/clientes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        then(clienteMapper).should().toEntity(any(ClienteRequestDto.class));
        then(clienteService).should().updateCliente(entity, id);
        then(clienteService).shouldHaveNoMoreInteractions();
    }

    @Test
    void updateCliente_whenNameIsNull_returns400() throws Exception {
        // Given
        Long id = 12L;
        ClienteRequestDto dto = getClientDTO(id);
        dto.setNombreCliente(null);

        // When + Then
        mockMvc.perform(put("/api/clientes/{id}", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        then(clienteService).shouldHaveNoInteractions();
    }

    @Test
    void listarClientes_whenClientsExist_returns200AndList() throws Exception {
        // Given
        Long id = 12L;
        Cliente cliente1 = getClientEntity(id);
        Cliente cliente2 = getClientEntity(13L);

        given(clienteService.listarClientes())
                .willReturn(List.of(cliente1, cliente2));

        // When + Then
        mockMvc.perform(get("/api/clientes")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].nombre").value("Juan"))
                .andExpect(jsonPath("$[1].id").value(13))
                .andExpect(jsonPath("$[1].nombre").value("Juan"));

        then(clienteService).should().listarClientes();
    }

    @Test
    void eliminarCliente_whenClientExists_returns200() throws Exception {
        Long id = 12L;

        // When + Then
        mockMvc.perform(delete("/api/clientes/{id}", id))
                .andExpect(status().isNoContent());

        then(clienteService).should().eliminarCliente(id);
        then(clienteService).shouldHaveNoMoreInteractions();
    }

    @Test
    void deleteClient_whenIdIsInvalid_returnsBadRequest() throws Exception {
        // Given
        var id = "invalidId";

        // When
        mockMvc.perform(delete("/api/clientes/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Then
        then(clienteService).should(never()).eliminarCliente(any());
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

    private Cliente getClientEntity(Long id) {
        var cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre("Juan");
        cliente.setEmail("juan@mail.com");
        cliente.setTelefono("1122334455");
        cliente.setCreadoEn(LocalDateTime.of(2026, 2, 24, 21, 0));

        return cliente;
    }

}
