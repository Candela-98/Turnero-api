package com.turnero.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ClienteRequestDto;
import com.turnero.api.mapper.ClienteMapper;
import com.turnero.api.model.Cliente;
import com.turnero.api.repository.ClienteRepository;

import com.turnero.api.service.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClienteControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ClienteMapper clienteMapper;

    @Autowired
    ClienteService clienteService;

    @Autowired
    ClienteRepository clienteRepository;

    @BeforeEach
    void cleanDb() {
        clienteRepository.deleteAll();
    }

    @Test
    void altaCliente_whenRequestIsValid_persistsClient_andReturns200() throws Exception {
        // Given
        ClienteRequestDto dto = new ClienteRequestDto();
        dto.setNombreCliente("Juan Perez");
        dto.setEmail("juan@mail.com");
        dto.setTelCliente("1122334455");
        LocalDateTime fecha = LocalDateTime.of(2026, 2, 24, 21, 0);
        dto.setFechaCreacion(fecha);

        // When
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Then
        List<Cliente> todos = clienteRepository.findAll();

        assertThat(todos).hasSize(1);
        Cliente guardado = todos.get(0);
        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getNombre()).isEqualTo("Juan Perez");
        assertThat(guardado.getEmail()).isEqualTo("juan@mail.com");
        assertThat(guardado.getTelefono()).isEqualTo("1122334455");
        assertThat(guardado.getCreadoEn()).isEqualTo(fecha);
    }
}
