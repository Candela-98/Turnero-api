package com.turnero.api.controller;

import com.turnero.api.dto.ClienteRequestDto;
import com.turnero.api.dto.ProfesionalRequestDto;
import com.turnero.api.mapper.ClienteMapper;
import com.turnero.api.model.Cliente;
import com.turnero.api.model.Profesional;
import com.turnero.api.model.Turno;
import com.turnero.api.service.ClienteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteController(ClienteService clienteService, ClienteMapper clienteMapper) {
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    @PostMapping
    public void altaCliente(@RequestBody ClienteRequestDto clienteDto) {
        var cliente = clienteMapper.toEntity(clienteDto);
        clienteService.altaCliente(cliente);
    }

    @GetMapping("/{id}")
    public Cliente buscarCliente(@PathVariable Long id) {
        return clienteService.buscarCliente(id);
    }

    @PutMapping("/{id}")
    public void updateCliente(@RequestBody ClienteRequestDto clienteDto, @PathVariable Long id) {
        var cliente = clienteMapper.toEntity(clienteDto);
        clienteService.updateCliente(cliente, id);
    }

    @GetMapping
    public List<Cliente> listarClientes() {
        return clienteService.listarClientes();
    }

    @DeleteMapping("/{id}")
    public void eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
    }
}
